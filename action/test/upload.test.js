import { test } from 'node:test';
import assert from 'node:assert/strict';
import { uploadWithRetry } from '../src/upload.js';
import { deadServerUrl, fakeClock, fakeServer, gunzip, parseMultipart } from './helpers.js';

const metadata = { commitSha: '3f2a9c1d', branch: 'main', provider: 'GITHUB', runId: '1', runAttempt: 1, jobKey: 'test', variant: '' };
const files = [{ name: 'target/surefire-reports/TEST-com.shop.CartTest.xml', content: Buffer.from('<testsuite name="x"/>') }];
const accepted = { status: 202, body: { uploadId: 7, runId: 3, status: 'RECEIVED', statusUrl: '/api/v1/uploads/7', runUrl: 'https://fw.example/p/shop/runs/3', duplicate: false } };

function options(serverUrl, clock, extra = {}) {
  return { serverUrl, token: 'fw_ing_secret', metadata, files, timeoutSeconds: 120, now: clock.now, sleep: clock.sleep, ...extra };
}

test('FR-CI-001: sends the metadata as JSON and each report gzip-compressed, with the token as a bearer header', async () => {
  const server = await fakeServer([accepted]);
  try {
    const result = await uploadWithRetry(options(server.url, fakeClock()));
    assert.equal(result.ok, true);
    assert.equal(result.body.runUrl, 'https://fw.example/p/shop/runs/3');

    const [request] = server.requests;
    assert.equal(request.method, 'POST');
    assert.equal(request.url, '/api/v1/uploads');
    assert.equal(request.headers.authorization, 'Bearer fw_ing_secret');
    const parts = parseMultipart(request);
    const meta = parts.find((p) => p.name === 'metadata');
    assert.equal(meta.contentType, 'application/json');
    assert.deepEqual(JSON.parse(meta.body.toString('utf8')), metadata);
    const file = parts.find((p) => p.name === 'files');
    assert.equal(file.filename, 'TEST-com.shop.CartTest.xml.gz');
    assert.equal(gunzip(file.body), '<testsuite name="x"/>');
  } finally {
    await server.close();
  }
});

test('FR-CI-003: a free-tier server waking up (503, 503, then 202) is retried with 2 s and 8 s pauses', async () => {
  const server = await fakeServer([{ status: 503 }, { status: 503 }, accepted]);
  const clock = fakeClock();
  try {
    const result = await uploadWithRetry(options(server.url, clock));
    assert.equal(result.ok, true);
    assert.equal(result.attempts, 3);
    assert.deepEqual(clock.sleeps, [2000, 8000]);
  } finally {
    await server.close();
  }
});

test('FR-CI-003: on 429 the Retry-After header decides the pause', async () => {
  const server = await fakeServer([{ status: 429, headers: { 'retry-after': '5' } }, accepted]);
  const clock = fakeClock();
  try {
    const result = await uploadWithRetry(options(server.url, clock));
    assert.equal(result.ok, true);
    assert.deepEqual(clock.sleeps, [5000]);
  } finally {
    await server.close();
  }
});

test('FR-ING-004: a conflict is reported at once, without retrying, and keeps the server reason', async () => {
  const server = await fakeServer([{ status: 409, body: { code: 'duplicate-upload-different-content', detail: 'already uploaded with different content' } }]);
  try {
    const result = await uploadWithRetry(options(server.url, fakeClock()));
    assert.equal(result.ok, false);
    assert.equal(server.requests.length, 1);
    assert.match(result.error, /409/);
    assert.match(result.error, /duplicate-upload-different-content/);
  } finally {
    await server.close();
  }
});

test('US-06 #4: a revoked token (401) is not retried', async () => {
  const server = await fakeServer([{ status: 401, body: { code: 'unauthorized' } }]);
  try {
    const result = await uploadWithRetry(options(server.url, fakeClock()));
    assert.equal(result.ok, false);
    assert.equal(server.requests.length, 1);
  } finally {
    await server.close();
  }
});

test('FR-CI-003: when the server is down, it tries 4 times in total and then gives up', async () => {
  const clock = fakeClock();
  const result = await uploadWithRetry(options(await deadServerUrl(), clock));
  assert.equal(result.ok, false);
  assert.equal(result.attempts, 4);
  assert.deepEqual(clock.sleeps, [2000, 8000, 30000]);
  assert.match(result.error, /could not reach/i);
});

test('FR-CI-003: retries stop when the time budget is used up', async () => {
  const server = await fakeServer([{ status: 503 }]);
  const clock = fakeClock();
  try {
    const result = await uploadWithRetry(options(server.url, clock, { timeoutSeconds: 15 }));
    assert.equal(result.ok, false);
    // 2 s and 8 s fit in 15 s; a further 30 s pause would not
    assert.deepEqual(clock.sleeps, [2000, 8000]);
    assert.equal(result.attempts, 3);
  } finally {
    await server.close();
  }
});

test('Stress: 20 report files of 2 MB each are compressed and sent in one request', async () => {
  const big = Buffer.from(`<testsuite name="big">${'<testcase classname="a.B" name="t"/>'.repeat(55_000)}</testsuite>`);
  const many = Array.from({ length: 20 }, (_, i) => ({ name: `reports/TEST-${i}.xml`, content: big }));
  const server = await fakeServer([accepted]);
  try {
    const result = await uploadWithRetry({ ...options(server.url, fakeClock()), files: many });
    assert.equal(result.ok, true);
    const parts = parseMultipart(server.requests[0]).filter((p) => p.name === 'files');
    assert.equal(parts.length, 20);
    assert.ok(server.requests[0].body.length < big.length, 'compressed request is smaller than one raw file');
  } finally {
    await server.close();
  }
});

test('FR-CI-003: a server that keeps rate-limiting is still retried at most 3 times', async () => {
  const server = await fakeServer([{ status: 429, headers: { 'retry-after': '1' } }]);
  const clock = fakeClock();
  try {
    const result = await uploadWithRetry(options(server.url, clock));
    assert.equal(result.ok, false);
    assert.equal(server.requests.length, 4);
    assert.deepEqual(clock.sleeps, [1000, 1000, 1000]);
  } finally {
    await server.close();
  }
});
