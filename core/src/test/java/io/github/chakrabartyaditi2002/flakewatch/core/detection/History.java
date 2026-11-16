package io.github.chakrabartyaditi2002.flakewatch.core.detection;

import io.github.chakrabartyaditi2002.flakewatch.core.report.ExecutionStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds readable test histories. Each call adds one upload (one CI job); uploads are one hour apart.
 *
 * <pre>
 * History.main().pass().pass().retryPass().fail()   // P P [F→P] F on the default branch
 * </pre>
 */
final class History {

    static final Instant NOW = Instant.parse("2026-11-16T12:00:00Z");
    static final String DEFAULT_BRANCH = "main";

    private final List<Execution> executions = new ArrayList<>();
    private String variant = "";
    private String branch = DEFAULT_BRANCH;
    private Instant nextTime = NOW.minus(Duration.ofDays(5));
    private int run;

    static History main() {
        return new History();
    }

    History variant(String v) {
        this.variant = v;
        return this;
    }

    History branch(String b) {
        this.branch = b;
        return this;
    }

    History daysAgo(int days) {
        this.nextTime = NOW.minus(Duration.ofDays(days));
        return this;
    }

    History pass() {
        return upload(newCommit(), ExecutionStatus.PASSED);
    }

    History fail() {
        return upload(newCommit(), ExecutionStatus.FAILED);
    }

    History skip() {
        return upload(newCommit(), ExecutionStatus.SKIPPED);
    }

    /** {@code [F→P]}: failed, then passed on retry within one upload. */
    History retryPass() {
        return upload(newCommit(), ExecutionStatus.FAILED, ExecutionStatus.PASSED);
    }

    /** One upload with the given executions in order, on the given commit. */
    History upload(String commit, ExecutionStatus... attempts) {
        run++;
        long runId = 1000L + run;
        for (int i = 0; i < attempts.length; i++) {
            executions.add(new Execution(
                    "GITHUB", "run-" + runId, 1, "default", variant, branch, commit, i, attempts[i], nextTime, runId));
        }
        nextTime = nextTime.plus(Duration.ofHours(1));
        return this;
    }

    /** A commit SHA unique to the next upload. */
    String newCommit() {
        return String.format("c%06x", run + 1);
    }

    List<Execution> executions() {
        return List.copyOf(executions);
    }

    Classification classify() {
        return Classifier.classify(executions, DetectionSettings.defaults(), NOW, DEFAULT_BRANCH);
    }
}
