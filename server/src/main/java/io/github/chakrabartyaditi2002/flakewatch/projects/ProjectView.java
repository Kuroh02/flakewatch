package io.github.chakrabartyaditi2002.flakewatch.projects;

import java.time.Instant;
import java.util.Map;

/** Matches {@code Project} in api/openapi.yaml. {@code viewerIsOwner} is false until sign-in exists (M2). */
public record ProjectView(
        String slug,
        String name,
        String visibility,
        String defaultBranch,
        String owner,
        Map<String, Integer> settings,
        Instant createdAt,
        boolean viewerIsOwner) {

    public static ProjectView of(ProjectRef p) {
        return new ProjectView(
                p.slug(),
                p.name(),
                p.visibility(),
                p.defaultBranch(),
                p.ownerLogin(),
                Map.of(
                        "windowDays", p.settings().windowDays(),
                        "suspectedRecoveries", p.settings().suspectedRecoveries(),
                        "brokenStreak", p.settings().brokenStreak()),
                p.createdAt(),
                false);
    }
}
