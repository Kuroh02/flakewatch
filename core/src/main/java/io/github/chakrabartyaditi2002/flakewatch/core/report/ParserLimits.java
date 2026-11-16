package io.github.chakrabartyaditi2002.flakewatch.core.report;

/**
 * Hard limits applied while parsing untrusted reports (NFR-SEC-002, PLAN §7.2).
 */
public record ParserLimits(
        int maxDepth, int maxTextChars, int maxTestCases, int maxMessageChars, int maxStackTraceChars) {

    public static ParserLimits defaults() {
        return new ParserLimits(64, 64 * 1024, 100_000, 4 * 1024, 16 * 1024);
    }
}
