package io.github.chakrabartyaditi2002.flakewatch.core.detection;

/**
 * Per-project detection settings (detection rules §10, FR-DET-011).
 *
 * @param windowDays only executions newer than this many days count (7–60, default 14)
 * @param suspectedRecoveries recoveries needed for Suspected (2–5, default 2)
 * @param brokenStreak failures in a row on the default branch for Broken (2–10, default 3)
 */
public record DetectionSettings(int windowDays, int suspectedRecoveries, int brokenStreak) {

    public static final int MIN_RESULTS_FOR_VERDICT = 3;
    public static final int MAX_EXECUTIONS_PER_VARIANT = 200;

    public DetectionSettings {
        requireRange("windowDays", windowDays, 7, 60);
        requireRange("suspectedRecoveries", suspectedRecoveries, 2, 5);
        requireRange("brokenStreak", brokenStreak, 2, 10);
    }

    public static DetectionSettings defaults() {
        return new DetectionSettings(14, 2, 3);
    }

    private static void requireRange(String name, int value, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(name + " must be between " + min + " and " + max + ", was " + value);
        }
    }
}
