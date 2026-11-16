package io.github.chakrabartyaditi2002.flakewatch.core.report;

import java.util.List;

/**
 * One test case and every execution of it found in one upload, in order.
 *
 * @param suite the report's {@code classname}, tidied (falls back to the suite name)
 * @param name the report's {@code name}, tidied
 * @param file the {@code file} attribute, if the producer writes one
 */
public record ParsedTestCase(String suite, String name, String file, List<ParsedExecution> executions) {

    public ParsedTestCase {
        executions = List.copyOf(executions);
    }

    /** The last execution decides the test's outcome in this upload. */
    public ParsedExecution finalExecution() {
        return executions.get(executions.size() - 1);
    }

    /** True when an earlier execution failed, so the test needed at least one retry. */
    public boolean wasRetried() {
        for (int i = 0; i < executions.size() - 1; i++) {
            if (executions.get(i).status().isFailure()) {
                return true;
            }
        }
        return false;
    }
}
