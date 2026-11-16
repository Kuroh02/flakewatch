package io.github.chakrabartyaditi2002.flakewatch.projects;

import io.github.chakrabartyaditi2002.flakewatch.core.detection.DetectionSettings;
import java.time.Instant;

/** What other features need to know about a project. */
public record ProjectRef(
        long id,
        String slug,
        String name,
        String visibility,
        String defaultBranch,
        DetectionSettings settings,
        String ownerLogin,
        Instant createdAt) {

    public boolean isPublic() {
        return "PUBLIC".equals(visibility);
    }
}
