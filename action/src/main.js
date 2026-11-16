import fs from 'node:fs';
import path from 'node:path';
import { collectMetadata } from './metadata.js';
import { uploadWithRetry } from './upload.js';

/** The server accepts at most this many files per upload (PLAN §7.2). */
export const MAX_FILES_PER_UPLOAD = 20;
const STATUS_POLLS = 6;
const STATUS_POLL_MS = 2000;

/**
 * The whole action. Returns the exit code. Fails open (Constitution P2): any problem is a warning,
 * and the step only fails when the user set `fail-on-error: true` (FR-CI-002).
 */
export async function run({ env, cwd, log, now, sleep, fetchImpl = fetch }) {
  const input = (name) => (env[`INPUT_${name.toUpperCase()}`] ?? '').trim();
  const failOnError = input('fail-on-error').toLowerCase() === 'true';
  const token = input('token');
  if (token) {
    log(`::add-mask::${token}`);
  }

  try {
    if (!token) {
      log("::notice::No Flakewatch token, so results were not uploaded. This is expected on pull requests from forks, where secrets aren't available.");
      return 0;
    }
    const serverUrl = input('server-url').replace(/\/+$/, '');
    if (!serverUrl) {
      return problem('The server-url input is empty.');
    }

    const files = findReports(cwd, input('files') || '**/TEST-*.xml');
    if (files.length === 0) {
      log('::warning::No report files matched, so nothing was uploaded. Check the `files` pattern.');
      return 0;
    }

    const inputs = { 'job-key': input('job-key'), variant: input('variant') };
    const metadata = collectMetadata({ env, event: readEvent(env), inputs });
    const timeoutSeconds = Number.parseInt(input('timeout-seconds'), 10) || 120;

    const uploaded = [];
    for (let i = 0; i < files.length; i += MAX_FILES_PER_UPLOAD) {
      const batch = files.slice(i, i + MAX_FILES_PER_UPLOAD).map((file) => ({ name: file, content: fs.readFileSync(path.join(cwd, file)) }));
      const part = i / MAX_FILES_PER_UPLOAD + 1;
      const batchMetadata = part === 1 ? metadata : { ...metadata, jobKey: `${metadata.jobKey}~${part}` };
      const result = await uploadWithRetry({ serverUrl, token, metadata: batchMetadata, files: batch, timeoutSeconds, now, sleep, fetchImpl });
      if (!result.ok) {
        return problem(`Upload to Flakewatch failed: ${result.error}`);
      }
      uploaded.push(result.body);
    }

    const runUrl = uploaded[0]?.runUrl;
    const totals = await waitForCounts({ serverUrl, token, uploads: uploaded, now, sleep, fetchImpl });
    writeSummary(env, files.length, runUrl, totals);
    if (runUrl && env.GITHUB_OUTPUT) {
      fs.appendFileSync(env.GITHUB_OUTPUT, `run-url=${runUrl}\n`);
    }
    log(`Uploaded ${files.length} report file(s) to Flakewatch: ${runUrl ?? '(no link returned)'}`);
    return 0;
  } catch (e) {
    return problem(`Flakewatch upload step hit an unexpected error: ${e.message}`);
  }

  function problem(message) {
    log(`${failOnError ? '::error::' : '::warning::'}${message}`);
    return failOnError ? 1 : 0;
  }
}

/** Finds report files for one or more glob patterns (one per line). Never looks inside node_modules or .git. */
export function findReports(cwd, patterns) {
  const found = new Set();
  for (const pattern of patterns.split('\n').map((p) => p.trim()).filter(Boolean)) {
    for (const file of fs.globSync(pattern, { cwd, exclude: (p) => /(^|[\\/])(node_modules|\.git)([\\/]|$)/.test(p) })) {
      if (!/(^|[\\/])(node_modules|\.git)[\\/]/.test(file) && fs.statSync(path.join(cwd, file)).isFile()) {
        found.add(file.split(path.sep).join('/'));
      }
    }
  }
  return [...found].sort();
}

function readEvent(env) {
  try {
    return env.GITHUB_EVENT_PATH ? JSON.parse(fs.readFileSync(env.GITHUB_EVENT_PATH, 'utf8')) : {};
  } catch {
    return {};
  }
}

/** Briefly polls each upload's status so the summary can show counts (FR-CI-005). Never fails the step. */
async function waitForCounts({ serverUrl, token, uploads, sleep, fetchImpl }) {
  const totals = { tests: 0, passed: 0, failed: 0, errored: 0, skipped: 0, retried: 0 };
  for (const upload of uploads) {
    if (!upload?.statusUrl) {
      return null;
    }
    let summary = null;
    for (let poll = 0; poll < STATUS_POLLS && !summary; poll++) {
      try {
        const response = await fetchImpl(`${serverUrl}${upload.statusUrl}`, {
          headers: { authorization: `Bearer ${token}` },
          signal: AbortSignal.timeout(10_000),
        });
        const body = response.ok ? await response.json() : {};
        if (body.status === 'PROCESSED' && body.summary) {
          summary = body.summary;
        } else if (body.status === 'FAILED' || !response.ok) {
          return null;
        }
      } catch {
        return null;
      }
      if (!summary && poll < STATUS_POLLS - 1) {
        await sleep(STATUS_POLL_MS);
      }
    }
    if (!summary) {
      return null;
    }
    for (const key of Object.keys(totals)) {
      totals[key] += Number(summary[key] ?? 0);
    }
  }
  return totals;
}

function writeSummary(env, fileCount, runUrl, totals) {
  if (!env.GITHUB_STEP_SUMMARY) {
    return;
  }
  const lines = ['### Flakewatch', ''];
  lines.push(`Uploaded ${fileCount} report file${fileCount === 1 ? '' : 's'}.${runUrl ? ` [Open this run in Flakewatch](${runUrl})` : ''}`);
  lines.push('');
  if (totals) {
    lines.push(
      `${totals.tests} tests: ${totals.passed} passed, ${totals.failed} failed, ${totals.errored} errored, ` +
        `${totals.skipped} skipped, ${totals.retried} passed on retry.`,
    );
  } else {
    lines.push('The results are still being processed. Open the link in a minute to see which failures are flaky.');
  }
  fs.appendFileSync(env.GITHUB_STEP_SUMMARY, `${lines.join('\n')}\n`);
}
