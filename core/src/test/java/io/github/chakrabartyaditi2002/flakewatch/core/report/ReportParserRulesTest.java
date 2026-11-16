package io.github.chakrabartyaditi2002.flakewatch.core.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Parser rules from PLAN §7.3 and the safety limits from NFR-SEC-002.
 *
 * <p>Real producer behaviour is covered by {@link ReportParserFixturesTest}. The few XML strings
 * built here exercise single parser rules (limits, the quarantine marker, file ordering) that no
 * real tool output shows on its own.
 */
class ReportParserRulesTest {

    private static ReportFile xml(String name, String content) {
        return new ReportFile(name, content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("NFR-SEC-002: an XXE external entity is rejected, never resolved")
    void rejectsXxe() {
        assertThatThrownBy(() -> Fixtures.parse("malicious/xxe-external-entity.xml"))
                .isInstanceOf(ReportParseException.class)
                .hasMessageContaining("DTDs are not allowed");
        assertThat(ReportParser.isJunitReport(Fixtures.bytes("malicious/xxe-external-entity.xml"))).isFalse();
    }

    @Test
    @DisplayName("NFR-SEC-002: entity expansion (billion laughs) is rejected")
    void rejectsEntityExpansion() {
        assertThatThrownBy(() -> Fixtures.parse("malicious/billion-laughs.xml"))
                .isInstanceOf(ReportParseException.class)
                .hasMessageContaining("DTDs are not allowed");
    }

    @Test
    @DisplayName("NFR-SEC-002: an external DTD is rejected")
    void rejectsExternalDtd() {
        assertThatThrownBy(() -> Fixtures.parse("malicious/external-dtd.xml"))
                .isInstanceOf(ReportParseException.class)
                .hasMessageContaining("DTDs are not allowed");
    }

    @Test
    @DisplayName("NFR-SEC-002: XML nested deeper than the limit is rejected")
    void rejectsDeepNesting() {
        StringBuilder sb = new StringBuilder("<testsuites>");
        sb.append("<x>".repeat(10_000)).append("</x>".repeat(10_000)).append("</testsuites>");
        assertThatThrownBy(() -> new ReportParser().parse(List.of(xml("deep.xml", sb.toString()))))
                .isInstanceOf(ReportParseException.class)
                .hasMessageContaining("nested deeper than 64");
    }

    @Test
    @DisplayName("NFR-SEC-002: a 1 MB attribute is trimmed with a warning, not stored whole")
    void trimsHugeAttribute() {
        String huge = "x".repeat(1_000_000);
        ParseResult r = new ReportParser().parse(List.of(xml(
                "big.xml",
                "<testsuite name=\"s\"><testcase classname=\"a.B\" name=\"t\"><failure message=\"" + huge
                        + "\"/></testcase></testsuite>")));
        ParsedExecution e = r.testCases().get(0).finalExecution();
        assertThat(e.message().length()).isLessThanOrEqualTo(ParserLimits.defaults().maxMessageChars());
        assertThat(r.warnings()).hasSize(1);
        assertThat(r.warnings().get(0).message()).contains("trimmed");
    }

    @Test
    @DisplayName("FR-ING-008: a file that isn't well-formed XML fails with its name and line")
    void notWellFormed() {
        assertThatThrownBy(() -> Fixtures.parse("malformed/not-well-formed.xml"))
                .isInstanceOf(ReportParseException.class)
                .hasMessageContaining("file not-well-formed.xml, line ");
    }

    @Test
    @DisplayName("FR-ING-008: content that isn't XML, or has another root element, is not a JUnit report")
    void notAReport() {
        assertThat(ReportParser.isJunitReport("hello".getBytes(StandardCharsets.UTF_8))).isFalse();
        assertThat(ReportParser.isJunitReport(new byte[0])).isFalse();
        assertThat(ReportParser.isJunitReport("<html><body/></html>".getBytes(StandardCharsets.UTF_8))).isFalse();
        assertThat(ReportParser.isJunitReport("<testsuite name=\"x\"/>".getBytes(StandardCharsets.UTF_8))).isTrue();
    }

    @Test
    @DisplayName("FR-ING-009: a testcase without a name is skipped with a warning; classname falls back to the suite")
    void tolerantOfSmallProblems() {
        ParseResult r = Fixtures.parse("malformed/missing-names.xml");
        assertThat(r.testCases()).hasSize(2);
        ParsedTestCase fallback = r.testCases().get(0);
        assertThat(fallback.suite()).isEqualTo("fallback.Suite");
        assertThat(fallback.name()).isEqualTo("usesSuiteName");
        assertThat(fallback.finalExecution().durationMs()).isEqualTo(1_234_500L);
        assertThat(r.testCases().get(1).finalExecution().durationMs()).isEqualTo(0L);
        assertThat(r.warnings()).hasSize(2);
        assertThat(r.warnings().get(0).message()).contains("without a name");
        assertThat(r.warnings().get(0).line()).isEqualTo(4);
        assertThat(r.warnings().get(1).message()).contains("invalid time");
    }

    @Test
    @DisplayName("FR-ING-013: a skipped entry carrying the quarantine marker is a quarantined failure")
    void quarantineMarker() {
        ParseResult r = new ReportParser().parse(List.of(xml(
                "q.xml",
                "<testsuite name=\"s\"><testcase classname=\"a.B\" name=\"t\"><skipped message=\""
                        + ReportParser.QUARANTINE_MARKER + " expected 1 but was 2\"/></testcase>"
                        + "<testcase classname=\"a.B\" name=\"plain\"><skipped/></testcase></testsuite>")));
        ParsedExecution muted = r.testCases().get(0).finalExecution();
        assertThat(muted.status()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(muted.quarantined()).isTrue();
        assertThat(muted.message()).isEqualTo("expected 1 but was 2");
        assertThat(r.testCases().get(1).finalExecution().status()).isEqualTo(ExecutionStatus.SKIPPED);
    }

    @Test
    @DisplayName("FR-ING-007: repeated entries across files are ordered by file name, then position")
    void repeatedEntriesAcrossFiles() {
        String pass = "<testsuite name=\"s\"><testcase classname=\"a.B\" name=\"t\"/></testsuite>";
        String fail = "<testsuite name=\"s\"><testcase classname=\"a.B\" name=\"t\"><failure message=\"x\"/></testcase></testsuite>";
        ParseResult r = new ReportParser().parse(List.of(xml("b-second.xml", pass), xml("a-first.xml", fail)));
        assertThat(r.testCases()).hasSize(1);
        List<ParsedExecution> ex = r.testCases().get(0).executions();
        assertThat(ex.get(0).status()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(ex.get(1).status()).isEqualTo(ExecutionStatus.PASSED);
        assertThat(ex.get(1).index()).isEqualTo(1);
    }

    @Test
    @DisplayName("FR-ING-010: failure messages and stack traces are trimmed to their storage limits")
    void trimsFailureDetails() {
        ParserLimits limits = new ParserLimits(64, 64 * 1024, 100, 10, 20);
        ParseResult r = new ReportParser(limits).parse(List.of(xml(
                "t.xml",
                "<testsuite name=\"s\"><testcase classname=\"a.B\" name=\"t\"><failure message=\"0123456789ABCDEF\">"
                        + "x".repeat(100) + "</failure></testcase></testsuite>")));
        ParsedExecution e = r.testCases().get(0).finalExecution();
        assertThat(e.message()).hasSize(10);
        assertThat(e.stackTrace()).hasSize(20);
    }

    @Test
    @DisplayName("NFR-SEC-002: more test cases than the limit fails the upload")
    void testCaseLimit() {
        ParserLimits limits = new ParserLimits(64, 1024, 2, 100, 100);
        String report = "<testsuite name=\"s\"><testcase classname=\"a\" name=\"1\"/><testcase classname=\"a\" name=\"2\"/>"
                + "<testcase classname=\"a\" name=\"3\"/></testsuite>";
        assertThatThrownBy(() -> new ReportParser(limits).parse(List.of(xml("many.xml", report))))
                .isInstanceOf(ReportParseException.class)
                .hasMessageContaining("more than 2 test cases");
    }
}
