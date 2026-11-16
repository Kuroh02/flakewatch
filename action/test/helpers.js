import http from 'node:http';
import zlib from 'node:zlib';

/**
 * A local stand-in for the Flakewatch server. Each request gets the next scripted reply, and
 * everything the action sent is recorded so tests can inspect it.
 */
export async function fakeServer(replies) {
  const requests = [];
  const queue = [...replies];
  const server = http.createServer((req, res) => {
    const chunks = [];
    req.on('data', (c) => chunks.push(c));
    req.on('end', () => {
      requests.push({ method: req.method, url: req.url, headers: req.headers, body: Buffer.concat(chunks) });
      const reply = queue.length > 1 ? queue.shift() : queue[0];
      res.writeHead(reply.status, { 'content-type': 'application/json', ...(reply.headers ?? {}) });
      res.end(JSON.stringify(reply.body ?? {}));
    });
  });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const { port } = server.address();
  return {
    url: `http://127.0.0.1:${port}`,
    requests,
    close: () => new Promise((resolve) => server.close(resolve)),
  };
}

/** A URL nothing listens on: what the runner sees when the Flakewatch host is down. */
export async function deadServerUrl() {
  const server = http.createServer();
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const { port } = server.address();
  await new Promise((resolve) => server.close(resolve));
  return `http://127.0.0.1:${port}`;
}

/** Fake time: sleeping advances the clock instantly, and every pause is recorded. */
export function fakeClock(start = 1_700_000_000_000) {
  let now = start;
  const sleeps = [];
  return {
    now: () => now,
    sleep: async (ms) => {
      sleeps.push(ms);
      now += ms;
    },
    sleeps,
  };
}

/** Splits a multipart body into its parts (enough for what the action sends). */
export function parseMultipart(request) {
  const boundary = /boundary=(.+)$/.exec(request.headers['content-type'])[1];
  const text = request.body.toString('latin1');
  return text
    .split(`--${boundary}`)
    .slice(1, -1)
    .map((raw) => {
      const [head, ...rest] = raw.split('\r\n\r\n');
      const body = Buffer.from(rest.join('\r\n\r\n').replace(/\r\n$/, ''), 'latin1');
      return {
        name: /name="([^"]+)"/.exec(head)?.[1],
        filename: /filename="([^"]+)"/.exec(head)?.[1],
        contentType: /content-type: ([^\r\n]+)/i.exec(head)?.[1],
        body,
      };
    });
}

export function gunzip(buffer) {
  return zlib.gunzipSync(buffer).toString('utf8');
}

/** The environment a GitHub-hosted runner gives a job on a push to main. */
export function pushEnv(overrides = {}) {
  return {
    GITHUB_SHA: '3f2a9c1d8e7b6a5f4e3d2c1b0a9f8e7d6c5b4a39',
    GITHUB_REF_NAME: 'main',
    GITHUB_HEAD_REF: '',
    GITHUB_RUN_ID: '9876543210',
    GITHUB_RUN_ATTEMPT: '1',
    GITHUB_JOB: 'test',
    GITHUB_SERVER_URL: 'https://github.com',
    GITHUB_REPOSITORY: 'aditi/shop',
    GITHUB_EVENT_NAME: 'push',
    ...overrides,
  };
}
