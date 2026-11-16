package io.github.chakrabartyaditi2002.flakewatch.failures;

import io.github.chakrabartyaditi2002.flakewatch.platform.db.PageRequest;
import io.github.chakrabartyaditi2002.flakewatch.projects.ProjectAccess;
import io.github.chakrabartyaditi2002.flakewatch.projects.ProjectRef;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Failure groups list and detail (FR-GRP-003, FR-UI-005). */
@RestController
@RequestMapping("/api/v1/projects/{slug}/failure-groups")
public class FailureGroupsController {

    private final ProjectAccess access;
    private final FailureGroupQueries groups;

    public FailureGroupsController(ProjectAccess access, FailureGroupQueries groups) {
        this.access = access;
        this.groups = groups;
    }

    @GetMapping
    public PageRequest.Page<FailureGroupView> list(
            @PathVariable String slug,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor) {
        ProjectRef project = access.readable(slug);
        return groups.list(project.id(), PageRequest.of(limit, cursor));
    }

    @GetMapping("/{groupId}")
    public FailureGroupView detail(@PathVariable String slug, @PathVariable long groupId) {
        ProjectRef project = access.readable(slug);
        return groups.detail(project.id(), groupId).orElseThrow(ProjectAccess::notFound);
    }
}
