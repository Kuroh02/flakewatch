package io.github.chakrabartyaditi2002.flakewatch.core.detection;

import io.github.chakrabartyaditi2002.flakewatch.core.report.ExecutionStatus;
import java.time.Instant;
import java.util.Comparator;

/**
 * One execution of one test case, with the run fields the detector needs (detection rules §2).
 *
 * @param runId the database ID of the CI run; used only to link evidence, never to order or identify
 */
public record Execution(
        String provider,
        String externalRunId,
        int attempt,
        String jobKey,
        String variant,
        String branch,
        String commitSha,
        int executionIndex,
        ExecutionStatus status,
        Instant executedAt,
        long runId) {

    public Execution {
        variant = variant == null ? "" : variant;
    }

    /** The upload this execution belongs to: run + attempt + job key. */
    String uploadKey() {
        return provider + "\u0000" + externalRunId + "\u0000" + attempt + "\u0000" + jobKey;
    }

    /** Two executions with the same identity are the same execution (detection rules §3, step 2). */
    String identity() {
        return uploadKey() + "\u0000" + executionIndex;
    }

    /** The data-only ordering from detection rules §3, step 3. */
    static final Comparator<Execution> ORDER = Comparator.comparing(Execution::executedAt)
            .thenComparing(Execution::provider)
            .thenComparing(Execution::externalRunId)
            .thenComparingInt(Execution::attempt)
            .thenComparing(Execution::jobKey)
            .thenComparingInt(Execution::executionIndex);

    /** A total order over every field, so picking one of several duplicates never depends on input order. */
    static final Comparator<Execution> TOTAL = ORDER.thenComparing(Execution::variant)
            .thenComparing(Execution::branch)
            .thenComparing(Execution::commitSha)
            .thenComparing(Execution::status)
            .thenComparingLong(Execution::runId);
}
