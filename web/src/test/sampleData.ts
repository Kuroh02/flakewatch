/** Realistic API answers for page tests, shaped exactly like api/openapi.yaml. */

export const demoProject = {
  slug: 'demo',
  name: 'Shop demo',
  visibility: 'PUBLIC',
  defaultBranch: 'main',
  owner: null,
  settings: { windowDays: 14, suspectedRecoveries: 2, brokenStreak: 3 },
  createdAt: '2026-11-01T09:00:00Z',
  viewerIsOwner: false,
};

export const flakyCheckout = {
  id: 42,
  testKey: 'com.shop.CheckoutTest#paysWithCard',
  suite: 'com.shop.CheckoutTest',
  name: 'paysWithCard',
  status: 'FLAKY',
  statusSince: '2026-11-14T03:00:00Z',
  impact: 6,
  flipRate: 0.4,
  lastFailureAt: '2026-11-16T03:10:00Z',
  quarantined: false,
};

export const suspectedSearch = {
  ...flakyCheckout,
  id: 43,
  testKey: 'com.shop.SearchTest#findsShoes',
  suite: 'com.shop.SearchTest',
  name: 'findsShoes',
  status: 'SUSPECTED',
  impact: 2,
};

export const nightlyRun = {
  id: 7,
  provider: 'GITHUB',
  externalRunId: '9876543210',
  attempt: 1,
  commitSha: '3f2a9c1d8e7b6a5f',
  branch: 'main',
  pullRequest: null,
  startedAt: '2026-11-16T03:00:00Z',
  totals: { tests: 120, passed: 116, failed: 3, errored: 0, skipped: 1, retried: 1 },
  failedTests: 3,
};
