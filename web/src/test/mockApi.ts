import { vi } from 'vitest';

/**
 * Replaces fetch with canned API answers, keyed by path (query string ignored unless the key has one).
 * Returns the mock so tests can check which URLs were requested.
 */
export function mockApi(answers: Record<string, unknown>) {
  const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
    const url = new URL(String(input), 'http://localhost');
    const body = answers[url.pathname + url.search] ?? answers[url.pathname];
    if (body === undefined) {
      return new Response(JSON.stringify({ status: 404, title: 'Not Found', code: 'not-found' }), {
        status: 404,
        headers: { 'content-type': 'application/problem+json' },
      });
    }
    return new Response(JSON.stringify(body), { status: 200, headers: { 'content-type': 'application/json' } });
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

/** Puts the browser at a URL before rendering, as if the user opened a shared link. */
export function openAt(path: string) {
  window.history.replaceState(null, '', path);
}
