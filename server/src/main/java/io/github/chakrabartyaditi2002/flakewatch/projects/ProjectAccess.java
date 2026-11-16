package io.github.chakrabartyaditi2002.flakewatch.projects;

import io.github.chakrabartyaditi2002.flakewatch.platform.error.ApiException;
import io.github.chakrabartyaditi2002.flakewatch.projects.internal.ProjectRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * The single place that decides who may see which project (FR-PRJ-004, PLAN §12.3).
 *
 * <p>Until GitHub sign-in exists (M2), nobody is signed in, so private projects are visible to
 * no one through the dashboard API. A private project always answers 404, never 403, so its
 * existence isn't revealed.
 */
@Service
public class ProjectAccess {

    private final ProjectRepository projects;

    public ProjectAccess(ProjectRepository projects) {
        this.projects = projects;
    }

    public ProjectRef readable(String slug) {
        return projects.findBySlug(slug)
                .filter(ProjectRef::isPublic)
                .orElseThrow(ProjectAccess::notFound);
    }

    /** For machine callers: the project their token belongs to. */
    public ProjectRef byId(long projectId) {
        return projects.findById(projectId).orElseThrow(ProjectAccess::notFound);
    }

    public static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "not-found", "Not found");
    }
}
