describe('Sample Jest Suite', () => {
  test('passing test', () => {
    expect(1 + 1).toBe(2);
  });

  test('failing test', () => {
    expect(true).toBe(false);
  });

  test('erroring test', () => {
    throw new Error('Unexpected runtime exception');
  });

  test.skip('skipped test', () => {
    expect(1).toBe(1);
  });

  test.each([
    ['alpha', true],
    ['beta', false],
  ])('parameterized test with input %s', (name, expected) => {
    if (!expected) {
      throw new Error(`Failure on ${name}`);
    }
  });
});
