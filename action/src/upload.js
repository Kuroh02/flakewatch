import path from 'node:path';
import zlib from 'node:zlib';

/** Pauses before retries 1, 2 and 3 (PLAN §14). */
export const BACKOFF_MS = [2000, 8000, 30000];

const RETRYABLE = (status) => status === 429 || status >= 500;

/**
 * Uploads the reports, retrying on network errors, 429 and 5xx (FR-CI-003).
 * Never throws: the result says whether it worked, so the caller can fail open (FR-CI-002).
 *
 * @returns {Promise<{ok: boolean, status?: number, body?: object, attempts: number, error?: string}>}
 */
export async function uploadWithRetry({ serverUrl, token, metadata, files, timeoutSeconds, now, sleep, fetchImpl = fetch }) {
  const url = `${serverUrl.replace(/\/+$/, '')}/api/v1/uploads`;
  const deadline = now() + timeoutSeconds * 1000;
  const parts = files.map((f) => ({ name: `${path.basename(f.name)}.gz`, gzip: zlib.gzipSync(f.content) }));
  let lastError = 'no attempt was made';

  for (let attempt = 1; ; attempt++) {
    let retryAfterMs = null;
    try {
      const form = new FormData();
      form.append('metadata', new Blob([JSON.stringify(metadata)], { type: 'application/json' }));
      for (const part of parts) {
        form.append('files', new Blob([part.gzip], { type: 'application/gzip' }), part.name);
      }
      const remaining = Math.max(1000, deadline - now());
      const response = await fetchImpl(url, {
        method: 'POST',
        headers: { authorization: `Bearer ${token}` },
        body: form,
        signal: AbortSignal.timeout(remaining),
      });
      const body = await readJson(response);
      if (response.ok) {
        return { ok: true, status: response.status, body, attempts: attempt };
      }
      lastError = `server answered ${response.status}${describeProblem(body)}`;
      if (!RETRYABLE(response.status)) {
        return { ok: false, status: response.status, body, attempts: attempt, error: lastError };
      }
      retryAfterMs = parseRetryAfter(response.headers.get('retry-after'));
    } catch (e) {
      lastError = `could not reach ${serverUrl} (${e.cause?.code ?? e.name ?? 'network error'})`;
    }

    const pause = retryAfterMs ?? BACKOFF_MS[attempt - 1];
    if (attempt > BACKOFF_MS.length || now() + pause >= deadline) {
      return { ok: false, attempts: attempt, error: lastError };
    }
    await sleep(pause);
  }
}

async function readJson(response) {
  try {
    return await response.json();
  } catch {
    return {};
  }
}

function describeProblem(body) {
  if (!body || typeof body !== 'object') {
    return '';
  }
  const parts = [body.code, body.detail].filter(Boolean);
  return parts.length ? `: ${parts.join(' - ')}` : '';
}

function parseRetryAfter(value) {
  const seconds = Number.parseInt(value ?? '', 10);
  return Number.isFinite(seconds) && seconds >= 0 ? seconds * 1000 : null;
}
