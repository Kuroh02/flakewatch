package io.github.chakrabartyaditi2002.flakewatch.core.report;

/** The result of one execution of a test case. ERRORED counts as a failure for detection. */
public enum ExecutionStatus {
    PASSED,
    FAILED,
    ERRORED,
    SKIPPED;

    public boolean isFailure() {
        return this == FAILED || this == ERRORED;
    }
}
