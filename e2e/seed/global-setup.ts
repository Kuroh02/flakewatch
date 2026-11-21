import { request, type APIRequestContext } from '@playwright/test';
import { readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { BROWSERS, PROJECT, RUNS, commitOf, reports, type Run } from './scenario';

export const SEED_FILE = fileURLToPath(new URL('../.seed.json', import.meta.url));
const REAL_FIXTURE = fileURLToPath(
  new URL('../../core/src/test/resources/fixtures/maven-surefire/TEST-sample.SampleTest.xml', import.meta.url),
);

export interface Seed {
  runs: Record<Run, number>;
  realRun: number;
  tests: Record<string, number>;
}

/**
 * Seeds the app the way CI would: real multipart uploads with an ingest token, then waits until the
 * background worker has processed them and detection has labelled the tests. Uploads are idempotent,
 * so running this again against the same database changes nothing.
 */
export default async function globalSetup(): Promise<void> {
  const token = process.env.FW_E2E_TOKEN;
  if (!token) {
    throw new Error('Set FW_E2E_TOKEN to the ingest token of the "e2e" project (see e2e/README.md).');
  }
  const api = await request.newContext({
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:8080',
    extraHTTPHeaders: { Authorization: `Bearer ${token}` },
  });

  const uploads: number[] = [];
  const runs = {} as Record<Run, number>;

  // A real report from the parser's fixtures, as a separate unit-test job before the browser runs.
  const real = await upload(api, {
    metadata: { commitSha: 'c0ffee000', branch: 'main', runId: 'e2e-0', jobKey: 'unit', runStartedAt: hoursAgo(5) },
    files: [{ name: 'TEST-sample.SampleTest.xml', xml: readFileSync(REAL_FIXTURE, 'utf8') }],
  });
  uploads.push(real.uploadId);

  for (const run of RUNS) {
    for (const browser of BROWSERS) {
      const accepted = await upload(api, {
        metadata: {
          commitSha: commitOf(run),
          branch: 'main',
          runId: `e2e-${run}`,
          jobKey: `ui-${browser}`,
          variant: browser,
          runStartedAt: hoursAgo(5 - run),
        },
        files: reports(run, browser).map((r) => ({ name: r.fileName, xml: r.xml() })),
      });
      uploads.push(accepted.uploadId);
      runs[run] = accepted.runId;
    }
  }

  for (const id of uploads) {
    await waitFor(`upload ${id} to be processed`, async () => {
      const status = (await (await api.get(`/api/v1/uploads/${id}`)).json()).status as string;
      if (status === 'FAILED') throw new Error(`Upload ${id} failed to process.`);
      return status === 'PROCESSED';
    });
  }

  // Detection runs as its own background job after processing; wait for the statuses it sets.
  const tests: Record<string, number> = {};
  for (const [name, status] of [
    ['refundsCard', 'BROKEN'],
    ['paysWithCard', 'FLAKY'],
    ['findsShoes', 'FLAKY'],
    ['addsItem', 'FAILING'],
  ] as const) {
    await waitFor(`${name} to be ${status}`, async () => {
      const body = await (await api.get(`/api/v1/projects/${PROJECT}/tests?q=${name}`)).json();
      const test = body.items.find((t: { name: string }) => t.name === name);
      if (test?.status === status) tests[name] = test.id;
      return test?.status === status;
    });
  }

  const seed: Seed = { runs, realRun: real.runId, tests };
  writeFileSync(SEED_FILE, JSON.stringify(seed, null, 2));
  await api.dispose();
}

async function upload(
  api: APIRequestContext,
  { metadata, files }: { metadata: Record<string, unknown>; files: { name: string; xml: string }[] },
): Promise<{ uploadId: number; runId: number }> {
  const form = new FormData();
  form.append('metadata', new Blob([JSON.stringify(metadata)], { type: 'application/json' }));
  for (const f of files) form.append('files', new Blob([f.xml], { type: 'application/xml' }), f.name);
  const response = await api.post('/api/v1/uploads', { multipart: form });
  if (response.status() !== 202 && response.status() !== 200) {
    throw new Error(`Upload ${JSON.stringify(metadata)} was refused: ${response.status()} ${await response.text()}`);
  }
  return response.json();
}

async function waitFor(what: string, check: () => Promise<boolean>, timeoutMs = 60_000): Promise<void> {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (await check()) return;
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  throw new Error(`Timed out after ${timeoutMs / 1000} s waiting for ${what}.`);
}

function hoursAgo(hours: number): string {
  return new Date(Date.now() - hours * 3_600_000).toISOString();
}

export function readSeed(): Seed {
  return JSON.parse(readFileSync(SEED_FILE, 'utf8')) as Seed;
}
