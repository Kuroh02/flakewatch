package io.github.chakrabartyaditi2002.flakewatch.core.detection;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The detector's verdict for one test case.
 *
 * @param byVariant status per variant, sorted by variant name ("" is the default variant)
 * @param evidence up to 5 most recent evidence items, newest first
 * @param impact failed final outcomes plus uploads that needed a retry, in the window (§9)
 * @param flipRate highest flip rate of any variant, between 0 and 1 (§9)
 * @param lastFailureAt newest failed execution in the window, or null
 */
public record Classification(
        Status overall,
        Map<String, Status> byVariant,
        List<Evidence> evidence,
        int impact,
        double flipRate,
        Instant lastFailureAt) {

    public Classification {
        byVariant = Collections.unmodifiableMap(new TreeMap<>(byVariant));
        evidence = List.copyOf(evidence);
    }

    /** A one-line reason for a status change, used in status events (FR-DET-009). */
    public String reason() {
        if (!evidence.isEmpty() && overall == Status.FLAKY) {
            return evidence.get(0).explanation();
        }
        return switch (overall) {
            case BROKEN -> "Failed on the latest default-branch runs in a row";
            case FLAKY -> "Proven: it passed and failed on the same code";
            case FAILING -> "Failed on the latest default-branch run";
            case SUSPECTED -> "Failed and recovered on its own repeatedly";
            case STABLE -> "Passing with no sign of flakiness";
            case NOT_ENOUGH_DATA -> "Fewer than 3 results so far";
        };
    }
}
