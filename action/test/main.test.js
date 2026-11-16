import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { run } from '../src/main.js';
import { deadServerUrl, fakeClock, fakeServer, pushEnv } from './helpers.js';

/** A temporary checkout with Surefire reports in it, like a runner after "mvn test". */
function workspace(reports = { 'target/surefire-reports/TEST-com.shop.CartTest.xml': '<testsuite name="com.shop.CartTest"/>' }) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'fw-action-'));
  for (const [file, content] of Object.entries(reports)) {
    fs.mkdirSync(path.join(dir, path.dirname(file)), { recursive: true });
    fs.writeFileSync(path.join(dir, file), content);
  }
  const summaryFile = path.join(dir, 'step-summary.md');
  const outputFile = path.join(dir, 'step-output.txt');
  fs.writeFileSync(summaryFile, '');
  fs.writeFileSync(outputFile, '');
  return { dir, summaryFile, outputFile };
}

function context(ws, serverUrl, inputOverrides = {}, envOverrides = {}) {
  const lines = [];
  const clock = fakeClock();
  const inputs = { 'server-url': serverUrl, token: 'fw_ing_s3cr3t', files: '**/TEST-*.xml', 'timeout-seconds': '120', 'fail-on-error': 'false', ...inputOverrides };
  const env = pushEnv({ GITHUB_STEP_SUMMARY: ws.summaryFile, GITHUB_OUTPUT: ws.outputFile, ...envOverrides });
  for (const [k, v] of Object.entries(inputs)) {
    env[`INPUT_${k.toUpperCase()}`] = v;
  }
  return { env, cwd: ws.dir, log: (line) => lines.push(line), now: clock.now, sleep: clock.sleep, lines };
}

const accepted = { status: 202, body: { uploadId: 7, runId: 3, status: 'RECEIVED', statusUrl: '/api/v1/uploads/7', runUrl: 'https://fw.example/p/shop/runs/3', duplicate: false } };
const processed = { status: 200, body: { uploadId: 7, runId: 3, status: 'PROCESSED', summary: { tests: 120, passed: 116, failed: 2, errored: 0, skipped: 1, retried: 1 }, warnings: [] } };

test('FR-CI-004: on a fork pull request (no secrets) it skips with a notice and the job stays green', async () => {
  const ws = workspace();
  const server = await fakeServer([accepted]);
  try {
    const ctx = context(ws, server.url, { token: '' });
    const code = await run(ctx);
    assert.equal(code, 0);
    assert.equal(server.requests.length, 0);
    assert.ok(ctx.lines.some((l) => l.startsWith('::notice::') && /token/i.test(l)));
  } finally {
    await server.close();
  }
});

test('FR-CI-001: when the tests produced no reports, it warns and succeeds', async () => {
  const ws = workspace({});
  const ctx = context(ws, 'http://127.0.0.1:9');
  assert.equal(await run(ctx), 0);
  assert.ok(ctx.lines.some((l) => l.startsWith('::warning::') && /no report files/i.test(l)));
});

test('FR-CI-005: after a successful upload the job summary shows the counts and a link to the run', async () => {
  const ws = workspace({
    'api/target/surefire-reports/TEST-com.shop.ApiTest.xml': '<testsuite name="a"/>',
    'web/target/surefire-reports/TEST-com.shop.WebTest.xml': '<testsuite name="b"/>',
    'node_modules/some-lib/TEST-ignored.xml': '<testsuite name="c"/>',
  });
  const server = await fakeServer([accepted, { status: 200, body: { ...processed.body, status: 'PROCESSING' } }, processed]);
  try {
    const ctx = context(ws, server.url);
    assert.equal(await run(ctx), 0);

    const upload = server.requests[0].body.toString('latin1');
    assert.ok(upload.includes('TEST-com.shop.ApiTest.xml.gz'));
    assert.ok(upload.includes('TEST-com.shop.WebTest.xml.gz'));
    assert.ok(!upload.includes('TEST-ignored'), 'node_modules is never searched');

    const summary = fs.readFileSync(ws.summaryFile, 'utf8');
    assert.match(summary, /https:\/\/fw\.example\/p\/shop\/runs\/3/);
    assert.match(summary, /120 tests/);
    assert.match(summary, /2 failed/);
    assert.match(summary, /1 passed on retry/);
    assert.match(fs.readFileSync(ws.outputFile, 'utf8'), /^run-url=https:\/\/fw\.example\/p\/shop\/runs\/3$/m);
  } finally {
    await server.close();
  }
});

test('P2 fail-open: when Flakewatch is down, the job result is unchanged', async () => {
  const ws = workspace();
  const ctx = context(ws, await deadServerUrl());
  assert.equal(await run(ctx), 0);
  assert.ok(ctx.lines.some((l) => l.startsWith('::warning::') && /could not reach/i.test(l)));
});

test('FR-CI-002: with fail-on-error: true, an upload problem fails the step', async () => {
  const ws = workspace();
  const ctx = context(ws, await deadServerUrl(), { 'fail-on-error': 'true' });
  assert.equal(await run(ctx), 1);
  assert.ok(ctx.lines.some((l) => l.startsWith('::error::')));
});

test('NFR-SEC-001: the token is masked first and never printed', async () => {
  const ws = workspace();
  const server = await fakeServer([{ status: 401, body: { code: 'unauthorized', detail: 'A valid project token is required' } }]);
  try {
    const ctx = context(ws, server.url);
    await run(ctx);
    assert.equal(ctx.lines[0], '::add-mask::fw_ing_s3cr3t');
    assert.ok(ctx.lines.slice(1).every((l) => !l.includes('fw_ing_s3cr3t')));
    assert.ok(!fs.readFileSync(ws.summaryFile, 'utf8').includes('fw_ing_s3cr3t'));
  } finally {
    await server.close();
  }
});

test('FR-CI-001: a server URL with a trailing slash still reaches /api/v1/uploads', async () => {
  const ws = workspace();
  const server = await fakeServer([accepted, processed]);
  try {
    const ctx = context(ws, `${server.url}/`);
    assert.equal(await run(ctx), 0);
    assert.equal(server.requests[0].url, '/api/v1/uploads');
  } finally {
    await server.close();
  }
});

test('FR-ING-001: a project with 45 test classes is sent as 3 uploads of at most 20 files, one job key each', async () => {
  const reports = {};
  for (let i = 1; i <= 45; i++) {
    reports[`target/surefire-reports/TEST-com.shop.Class${String(i).padStart(2, '0')}Test.xml`] = `<testsuite name="c${i}"/>`;
  }
  const ws = workspace(reports);
  const server = await fakeServer([accepted, processed]);
  try {
    const ctx = context(ws, server.url);
    assert.equal(await run(ctx), 0);
    const uploads = server.requests.filter((r) => r.method === 'POST');
    assert.equal(uploads.length, 3);
    const jobKeys = uploads.map((r) => /"jobKey":"([^"]+)"/.exec(r.body.toString('latin1'))[1]);
    assert.deepEqual(jobKeys, ['test', 'test~2', 'test~3']);
    const fileCounts = uploads.map((r) => (r.body.toString('latin1').match(/name="files"/g) ?? []).length);
    assert.deepEqual(fileCounts, [20, 20, 5]);
  } finally {
    await server.close();
  }
});
