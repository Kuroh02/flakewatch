import assert from 'node:assert';

describe('Sample WebdriverIO Suite', () => {
  it('passing test', () => {
    assert.strictEqual(1 + 1, 2);
  });

  it('failing test', () => {
    assert.strictEqual(true, false, 'Expected true to be false');
  });

  let retryCount = 0;
  it('flaky test with retry', function() {
    this.retries(1);
    if (retryCount++ === 0) {
      assert.fail('Flaky failure on attempt 1');
    }
  });

  it.skip('skipped test', () => {
    assert.ok(true);
  });

  ['alpha', 'beta'].forEach((param) => {
    it(`parameterized test for ${param}`, () => {
      if (param === 'beta') {
        assert.fail(`Failure for parameter ${param}`);
      } else {
        assert.strictEqual(param, 'alpha');
      }
    });
  });
});
