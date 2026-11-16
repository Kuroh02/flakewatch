package io.github.chakrabartyaditi2002.flakewatch.core.report;

import static io.github.chakrabartyaditi2002.flakewatch.core.report.ExecutionStatus.ERRORED;
import static io.github.chakrabartyaditi2002.flakewatch.core.report.ExecutionStatus.FAILED;
import static io.github.chakrabartyaditi2002.flakewatch.core.report.ExecutionStatus.PASSED;
import static io.github.chakrabartyaditi2002.flakewatch.core.report.ExecutionStatus.SKIPPED;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Each real fixture against hand-reviewed expectations (FR-ING-006, FR-ING-007).
 * The expectations were checked line by line against the XML files, not copied from parser output.
 */
class ReportParserFixturesTest {

    private static List<ExecutionStatus> statuses(ParseResult result, String name) {
        return result.testCases().stream()
                .filter(t -> t.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no test named " + name))
                .executions()
                .stream()
                .map(ParsedExecution::status)
                .toList();
    }

    @Test
    @DisplayName("FR-ING-006: every producer fixture is recognised as a JUnit report and parses without warnings")
    void allProducerFixturesParse() {
        for (String path : Fixtures.PRODUCER_FIXTURES) {
            assertThat(ReportParser.isJunitReport(Fixtures.bytes(path))).isTrue();
            ParseResult result = Fixtures.parse(path);
            assertThat(result.testCases()).isNotEmpty();
            assertThat(result.warnings()).isEmpty();
        }
    }

    @Test
    @DisplayName("FR-ING-007: Surefire flakyFailure becomes failed-then-passed; rerunFailure stays failed")
    void surefireRetries() {
        ParseResult r = Fixtures.parse("maven-surefire/TEST-sample.SampleTest.xml");
        assertThat(r.testCases()).hasSize(7);
        assertThat(statuses(r, "testFlaky")).containsExactly(FAILED, PASSED);
        assertThat(statuses(r, "testFailing")).containsExactly(FAILED, FAILED);
        assertThat(statuses(r, "testErroring")).containsExactly(ERRORED, ERRORED);
        assertThat(statuses(r, "testSkipped")).containsExactly(SKIPPED);
        assertThat(statuses(r, "testParameterized(String)[1]")).containsExactly(PASSED);

        ParsedTestCase flaky = r.testCases().stream().filter(t -> t.name().equals("testFlaky")).findFirst().orElseThrow();
        assertThat(flaky.suite()).isEqualTo("sample.SampleTest");
        assertThat(flaky.executions().get(0).index()).isEqualTo(0);
        assertThat(flaky.executions().get(1).index()).isEqualTo(1);
        assertThat(flaky.executions().get(0).message()).isEqualTo("Flaky failure on first attempt");
        assertThat(flaky.executions().get(0).failureType()).isEqualTo("org.opentest4j.AssertionFailedError");
        assertThat(flaky.wasRetried()).isTrue();
    }

    @Test
    @DisplayName("FR-ING-007: Gradle test-retry repeats test entries; executions are appended in order")
    void gradleRepeatedEntries() {
        ParseResult r = Fixtures.parse("gradle/TEST-sample.SampleGradleTest.xml");
        assertThat(statuses(r, "testFailing()")).containsExactly(FAILED, FAILED);
        assertThat(statuses(r, "[1] \"alpha\"")).containsExactly(PASSED, PASSED);
        assertThat(statuses(r, "testPassing()")).containsExactly(PASSED);
        assertThat(statuses(r, "testSkipped()")).containsExactly(SKIPPED);
    }

    @Test
    @DisplayName("FR-ING-006: TestNG JUnit-style report keeps parameters and reports the retried attempt as skipped")
    void testngReport() {
        ParseResult r = Fixtures.parse("testng/TEST-sample.SampleTestNGTest.xml");
        assertThat(r.testCases()).hasSize(6);
        assertThat(statuses(r, "testFlaky")).containsExactly(SKIPPED, PASSED);
        assertThat(statuses(r, "testParameterized[beta, false](2)")).containsExactly(FAILED);
    }

    @Test
    @DisplayName("FR-ING-008: TestNG's native testng-results.xml is not a JUnit report")
    void testngNativeFormatIsRejected() {
        assertThat(ReportParser.isJunitReport(Fixtures.bytes("testng/testng-results.xml"))).isFalse();
    }

    @Test
    @DisplayName("FR-ING-006: Playwright and WebdriverIO reports use file and browser names as suites")
    void javascriptReports() {
        ParseResult pw = Fixtures.parse("playwright/results.xml");
        assertThat(pw.testCases()).hasSize(6);
        assertThat(pw.testCases().get(0).suite()).isEqualTo("sample.spec.js");
        assertThat(statuses(pw, "failing test")).containsExactly(FAILED);
        assertThat(statuses(pw, "skipped test")).containsExactly(SKIPPED);

        ParseResult wdio = Fixtures.parse("webdriverio/results.xml");
        assertThat(wdio.testCases()).hasSize(6);
        assertThat(statuses(wdio, "flaky test with retry")).containsExactly(SKIPPED, PASSED);
        ParsedExecution failing = wdio.testCases().stream()
                .filter(t -> t.name().equals("failing test"))
                .findFirst()
                .orElseThrow()
                .finalExecution();
        assertThat(failing.message()).startsWith("Expected true to be false");
        assertThat(failing.stackTrace()).contains("sample.e2e.js");
    }

    @Test
    @DisplayName("FR-ING-006: jest-junit failures without a message attribute use the first line of the text")
    void jestReport() {
        ParseResult r = Fixtures.parse("jest-junit/junit.xml");
        assertThat(r.testCases()).hasSize(6);
        ParsedExecution failing = r.testCases().stream()
                .filter(t -> t.name().endsWith("failing test"))
                .findFirst()
                .orElseThrow()
                .finalExecution();
        assertThat(failing.status()).isEqualTo(FAILED);
        assertThat(failing.message()).startsWith("Error: expect(received).toBe(expected)");
    }
}
