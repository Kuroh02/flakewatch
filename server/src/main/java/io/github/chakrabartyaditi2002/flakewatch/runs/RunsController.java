package io.github.chakrabartyaditi2002.flakewatch.runs;

import io.github.chakrabartyaditi2002.flakewatch.platform.db.PageRequest;
import io.github.chakrabartyaditi2002.flakewatch.projects.ProjectAccess;
import io.github.chakrabartyaditi2002.flakewatch.projects.ProjectRef;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Runs list and run detail (FR-UI-004). */
@RestController
@RequestMapping("/api/v1/projects/{slug}/runs")
public class RunsController {

    private final ProjectAccess access;
    private final RunQueries runs;

    public RunsController(ProjectAccess access, RunQueries runs) {
        this.access = access;
        this.runs = runs;
    }

    @GetMapping
    public PageRequest.Page<RunSummary> list(
            @PathVariable String slug,
            @RequestParam(required = false) String branch,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor) {
        ProjectRef project = access.readable(slug);
        return runs.list(project.id(), branch == null || branch.isBlank() ? null : branch, PageRequest.of(limit, cursor));
    }

    @GetMapping("/{runId}")
    public RunDetail detail(@PathVariable String slug, @PathVariable long runId) {
        ProjectRef project = access.readable(slug);
        return runs.detail(project.id(), runId).orElseThrow(ProjectAccess::notFound);
    }
}
