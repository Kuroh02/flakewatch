import { test } from 'node:test';
import assert from 'node:assert/strict';
import { collectMetadata } from '../src/metadata.js';
import { pushEnv } from './helpers.js';

test('FR-CI-001: a push to main reports the commit, branch and run from the runner environment', () => {
  const metadata = collectMetadata({ env: pushEnv(), event: {}, inputs: {} });
  assert.deepEqual(metadata, {
    commitSha: '3f2a9c1d8e7b6a5f4e3d2c1b0a9f8e7d6c5b4a39',
    branch: 'main',
    provider: 'GITHUB',
    runId: '9876543210',
    runAttempt: 1,
    jobKey: 'test',
    variant: '',
    pullRequest: null,
    repositoryUrl: 'https://github.com/aditi/shop',
  });
});

test('FR-CI-001: a pull request reports its own branch and number, and the merge commit that actually ran', () => {
  const env = pushEnv({
    GITHUB_EVENT_NAME: 'pull_request',
    GITHUB_SHA: 'b1946ac92492d2347c6235b4d2611184aaaaaaaa',
    GITHUB_REF_NAME: '42/merge',
    GITHUB_HEAD_REF: 'feature/cart-badge',
  });
  const metadata = collectMetadata({ env, event: { pull_request: { number: 42 } }, inputs: {} });
  assert.equal(metadata.branch, 'feature/cart-badge');
  assert.equal(metadata.pullRequest, 42);
  assert.equal(metadata.commitSha, 'b1946ac92492d2347c6235b4d2611184aaaaaaaa');
});

test('FR-ING-004: "Re-run jobs" in the GitHub UI is a new attempt of the same run', () => {
  const metadata = collectMetadata({ env: pushEnv({ GITHUB_RUN_ATTEMPT: '2' }), event: {}, inputs: {} });
  assert.equal(metadata.runId, '9876543210');
  assert.equal(metadata.runAttempt, 2);
});

test('FR-ING-011: matrix jobs pass their own job key and variant, so shards of one run stay apart', () => {
  const metadata = collectMetadata({
    env: pushEnv({ GITHUB_JOB: 'e2e' }),
    event: {},
    inputs: { 'job-key': 'e2e-firefox-1', variant: 'firefox' },
  });
  assert.equal(metadata.jobKey, 'e2e-firefox-1');
  assert.equal(metadata.variant, 'firefox');
});

test('FR-CI-001: a missing or odd attempt number falls back to 1 instead of failing the job', () => {
  assert.equal(collectMetadata({ env: pushEnv({ GITHUB_RUN_ATTEMPT: '' }), event: {}, inputs: {} }).runAttempt, 1);
  assert.equal(collectMetadata({ env: pushEnv({ GITHUB_RUN_ATTEMPT: 'x' }), event: {}, inputs: {} }).runAttempt, 1);
});
