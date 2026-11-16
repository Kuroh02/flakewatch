package io.github.chakrabartyaditi2002.flakewatch.projects;

import io.github.chakrabartyaditi2002.flakewatch.platform.db.PageRequest;
import io.github.chakrabartyaditi2002.flakewatch.projects.internal.ProjectRepository;
import io.github.chakrabartyaditi2002.flakewatch.runs.RunQueries;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public projects and the project overview (FR-PRJ-004, FR-UI-001). */
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectsController {

    private final ProjectAccess access;
    private final ProjectRepository projects;
    private final RunQueries runs;
    private final JdbcClient jdbc;
    private final Clock clock;

    public ProjectsController(
            ProjectAccess access, ProjectRepository projects, RunQueries runs, JdbcClient jdbc, Clock clock) {
        this.access = access;
        this.projects = projects;
        this.runs = runs;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @GetMapping
    public PageRequest.Page<ProjectView> list(
            @RequestParam(required = false) Integer limit, @RequestParam(required = false) String cursor) {
        PageRequest page = PageRequest.of(limit, cursor);
        List<ProjectView> rows = projects.listPublic(page.offset(), page.fetchSize()).stream()
                .map(ProjectView::of)
                .toList();
        return page.toPage(rows);
    }

    @GetMapping("/{slug}")
    public ProjectView get(@PathVariable String slug) {
        return ProjectView.of(access.readable(slug));
    }

    @GetMapping("/{slug}/summary")
    public ProjectSummary summary(@PathVariable String slug) {
        ProjectRef project = access.readable(slug);
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String status : List.of("STABLE", "FLAKY", "SUSPECTED", "FAILING", "BROKEN", "NOT_ENOUGH_DATA")) {
            counts.put(status, 0);
        }
        jdbc.sql("""
                select coalesce(ts.status, 'NOT_ENOUGH_DATA') as status, count(*) as n
                  from test_cases tc
                  left join test_status ts on ts.test_case_id = tc.id
                 where tc.project_id = :project
                 group by 1
                """)
                .param("project", project.id())
                .query((rs, i) -> Map.entry(rs.getString("status"), rs.getInt("n")))
                .list()
                .forEach(e -> counts.put(e.getKey(), e.getValue()));
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();

        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        List<ProjectSummary.TrendPoint> trend = jdbc.sql("""
                select day, flaky, suspected, broken from project_daily_snapshots
                 where project_id = :project and day > :from
                 order by day
                """)
                .param("project", project.id())
                .param("from", today.minusDays(30))
                .query((rs, i) -> new ProjectSummary.TrendPoint(
                        rs.getObject("day", LocalDate.class), rs.getInt("flaky"), rs.getInt("suspected"), rs.getInt("broken")))
                .list();

        return new ProjectSummary(total, counts, trend, runs.recent(project.id(), 10));
    }
}
