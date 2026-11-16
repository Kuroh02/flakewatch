package io.github.chakrabartyaditi2002.flakewatch.tests;

import io.github.chakrabartyaditi2002.flakewatch.platform.db.PageRequest;
import io.github.chakrabartyaditi2002.flakewatch.platform.error.ApiException;
import io.github.chakrabartyaditi2002.flakewatch.projects.ProjectAccess;
import io.github.chakrabartyaditi2002.flakewatch.projects.ProjectRef;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Tests list, test detail and execution history (FR-UI-002, FR-UI-003). */
@RestController
@RequestMapping("/api/v1/projects/{slug}/tests")
public class TestsController {

    private final ProjectAccess access;
    private final TestQueries tests;

    public TestsController(ProjectAccess access, TestQueries tests) {
        this.access = access;
        this.tests = tests;
    }

    @GetMapping
    public PageRequest.Page<TestSummary> list(
            @PathVariable String slug,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor) {
        ProjectRef project = access.readable(slug);
        if (q != null && q.length() > 200) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid-query", "q must be at most 200 characters");
        }
        List<String> statuses = status == null || status.isBlank()
                ? List.of()
                : Arrays.stream(status.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
        return tests.list(project.id(), statuses, q, sort, PageRequest.of(limit, cursor));
    }

    @GetMapping("/{testId}")
    public TestDetail detail(@PathVariable String slug, @PathVariable long testId) {
        ProjectRef project = access.readable(slug);
        return tests.detail(project.id(), testId).orElseThrow(ProjectAccess::notFound);
    }

    @GetMapping("/{testId}/executions")
    public PageRequest.Page<ExecutionView> executions(
            @PathVariable String slug,
            @PathVariable long testId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor) {
        ProjectRef project = access.readable(slug);
        return tests.executions(project.id(), testId, PageRequest.of(limit, cursor)).orElseThrow(ProjectAccess::notFound);
    }
}
