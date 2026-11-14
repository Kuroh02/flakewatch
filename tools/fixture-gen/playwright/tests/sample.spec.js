const { test, expect } = require('@playwright/test');

test('passing test', async () => {
  expect(1 + 1).toBe(2);
});

test('failing test', async () => {
  expect(true).toBe(false);
});

test('flaky test with retry', async ({}, testInfo) => {
  if (testInfo.retry === 0) {
    expect(true).toBe(false);
  } else {
    expect(true).toBe(true);
  }
});

test.skip('skipped test', async () => {
  expect(1).toBe(1);
});

['alpha', 'beta'].forEach((param) => {
  test(`parameterized test for ${param}`, async () => {
    if (param === 'beta') {
      expect(param).toBe('gamma');
    } else {
      expect(param).toBe('alpha');
    }
  });
});
