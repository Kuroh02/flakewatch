package io.github.chakrabartyaditi2002.flakewatch.core.detection;

import java.util.List;

/** A test's status (FR-DET-001). See docs/design/detection-rules.md. */
public enum Status {
    STABLE,
    FLAKY,
    SUSPECTED,
    FAILING,
    BROKEN,
    NOT_ENOUGH_DATA;

    /** The order used to combine variants: the first one present wins (detection rules §6). */
    public static final List<Status> ATTENTION_ORDER =
            List.of(BROKEN, FLAKY, FAILING, SUSPECTED, STABLE, NOT_ENOUGH_DATA);

    public int attentionRank() {
        return ATTENTION_ORDER.indexOf(this);
    }
}
