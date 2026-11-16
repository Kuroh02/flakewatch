package io.github.chakrabartyaditi2002.flakewatch.core.detection;

import java.time.Instant;
import java.util.List;

/**
 * Proof that a test is flaky (FR-DET-007, detection rules §4).
 *
 * @param runIds database IDs of the run(s) that prove it, in ascending order
 */
public record Evidence(
        EvidenceKind kind, List<Long> runIds, String commitSha, String variant, Instant observedAt, String explanation) {

    public Evidence {
        runIds = List.copyOf(runIds);
    }

    public enum EvidenceKind {
        RETRY_PASS,
        SAME_COMMIT_MIXED
    }
}
