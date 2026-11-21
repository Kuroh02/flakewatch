/**
 * Builds Maven Surefire reports for the seed data.
 *
 * It writes only the element shapes found in the real fixture
 * core/src/test/resources/fixtures/maven-surefire/TEST-sample.SampleTest.xml (a plain <testcase>,
 * <failure> with a stack trace, <flakyFailure>/<rerunFailure> with a nested <stackTrace>), the same
 * way server/src/test/.../test/SurefireXml.java does for the integration tests.
 */
export class Surefire {
  private readonly cases: string[] = [];
  private readonly suite: string;

  private constructor(suite: string) {
    this.suite = suite;
  }

  static suite(className: string): Surefire {
    return new Surefire(className);
  }

  passed(test: string): this {
    this.cases.push(`${this.open(test)}/>`);
    return this;
  }

  /** Failed, then passed when Surefire re-ran it (rerunFailingTestsCount=1). */
  flaky(test: string, message: string, type = 'org.opentest4j.AssertionFailedError'): this {
    this.cases.push(`${this.open(test)}>${this.nested('flakyFailure', message, type)}</testcase>`);
    return this;
  }

  /** Failed, and failed again on the re-run. */
  failedTwice(test: string, message: string, type = 'org.opentest4j.AssertionFailedError'): this {
    this.cases.push(
      `${this.open(test)}>${this.failure(message, type)}${this.nested('rerunFailure', message, type)}</testcase>`,
    );
    return this;
  }

  get fileName(): string {
    return `TEST-${this.suite}.xml`;
  }

  xml(): string {
    return (
      `<?xml version="1.0" encoding="UTF-8"?>\n` +
      `<testsuite name="${esc(this.suite)}" tests="${this.cases.length}">\n  ${this.cases.join('\n  ')}\n</testsuite>\n`
    );
  }

  private open(test: string): string {
    return `<testcase name="${esc(test)}" classname="${esc(this.suite)}" time="0.120"`;
  }

  private trace(message: string, type: string): string {
    const simple = this.suite.slice(this.suite.lastIndexOf('.') + 1);
    return esc(`${type}: ${message}\n\tat ${this.suite}.check(${simple}.java:42)\n`);
  }

  private failure(message: string, type: string): string {
    return `<failure message="${esc(message)}" type="${type}">${this.trace(message, type)}</failure>`;
  }

  private nested(element: string, message: string, type: string): string {
    return `<${element} message="${esc(message)}" type="${type}"><stackTrace>${this.trace(message, type)}</stackTrace></${element}>`;
  }
}

function esc(text: string): string {
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}
