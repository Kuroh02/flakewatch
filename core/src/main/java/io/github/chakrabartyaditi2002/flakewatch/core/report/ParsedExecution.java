package io.github.chakrabartyaditi2002.flakewatch.core.report;

/**
 * One attempt at running a test case inside one upload.
 *
 * @param index position within the upload: 0 is the first try, 1 the first retry, and so on
 * @param failureType only set for failures and errors
 * @param message only set for failures and errors, trimmed to {@link ParserLimits#maxMessageChars()}
 * @param stackTrace only set for failures and errors, trimmed to {@link ParserLimits#maxStackTraceChars()}
 * @param quarantined true when a quarantine library muted this failure (FR-ING-013)
 */
public record ParsedExecution(
        int index,
        ExecutionStatus status,
        long durationMs,
        String failureType,
        String message,
        String stackTrace,
        boolean quarantined) {

    public ParsedExecution withIndex(int newIndex) {
        return new ParsedExecution(newIndex, status, durationMs, failureType, message, stackTrace, quarantined);
    }
}
