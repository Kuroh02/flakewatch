package io.github.chakrabartyaditi2002.flakewatch.tests;

import io.github.chakrabartyaditi2002.flakewatch.platform.db.Db;
import io.github.chakrabartyaditi2002.flakewatch.platform.db.PageRequest;
import io.github.chakrabartyaditi2002.flakewatch.platform.error.ApiException;
import io.github.chakrabartyaditi2002.flakewatch.platform.json.Json;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.support.SqlArrayValue;
import org.springframework.stereotype.Component;

/** Read side for test cases (FR-UI-002, FR-UI-003, FR-DET-007, FR-DET-012). Every query is scoped by project. */
@Component
public class TestQueries {

    public static final Set<String> STATUSES =
            Set.of("STABLE", "FLAKY", "SUSPECTED", "FAILING", "BROKEN", "NOT_ENOUGH_DATA");

    /** Shared by the tests list and failure-group detail. Tests not classified yet show as Not enough data. */
    public static final String SUMMARY_COLUMNS = """
            tc.id, tc.test_key, tc.suite, tc.name, tc.method_key, tc.file,
            coalesce(ts.status, 'NOT_ENOUGH_DATA') as status,
            coalesce(ts.status_since, tc.first_seen_at) as status_since,
            coalesce(ts.impact, 0) as impact,
            coalesce(ts.flip_rate, 0) as flip_rate,
            ts.last_failure_at
            """;

    private final JdbcClient jdbc;

    public TestQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public PageRequest.Page<TestSummary> list(
            long projectId, List<String> statuses, String query, String sort, PageRequest page) {
        for (String s : statuses) {
            if (!STATUSES.contains(s)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "invalid-status", "Unknown status " + s);
            }
        }
        String order = switch (sort == null ? "impact" : sort) {
            case "impact" -> "coalesce(ts.impact, 0) desc, ts.last_failure_at desc nulls last, tc.test_key, tc.id";
            case "recent" -> "ts.last_failure_at desc nulls last, tc.last_seen_at desc, tc.id";
            case "name" -> "tc.test_key, tc.id";
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "invalid-sort", "sort must be impact, recent or name");
        };
        String like = query == null || query.isBlank() ? null : "%" + escapeLike(query.strip()) + "%";
        List<TestSummary> rows = jdbc.sql("select " + SUMMARY_COLUMNS + """
                  from test_cases tc
                  left join test_status ts on ts.test_case_id = tc.id
                 where tc.project_id = :project
                   and (cardinality(:statuses) = 0 or coalesce(ts.status, 'NOT_ENOUGH_DATA') = any(:statuses))
                   and (cast(:like as text) is null or tc.test_key ilike :like)
                 order by %s
                 offset :offset limit :limit
                """.formatted(order))
                .param("project", projectId)
                .param("statuses", new SqlArrayValue("text", statuses.toArray()))
                .param("like", like)
                .param("offset", page.offset())
                .param("limit", page.fetchSize())
                .query(TestQueries::summary)
                .list();
        return page.toPage(rows);
    }

    public Optional<TestDetail> detail(long projectId, long testId) {
        record Head(TestSummary summary, String methodKey, String file, String byVariant, String evidence) {}
        Optional<Head> head = jdbc.sql("select " + SUMMARY_COLUMNS + """
                       , coalesce(ts.by_variant, '{}'::jsonb)::text as by_variant
                       , coalesce(ts.evidence, '[]'::jsonb)::text as evidence
                  from test_cases tc
                  left join test_status ts on ts.test_case_id = tc.id
                 where tc.project_id = :project and tc.id = :id
                """)
                .param("project", projectId)
                .param("id", testId)
                .query((rs, i) -> new Head(
                        summary(rs, i), rs.getString("method_key"), rs.getString("file"),
                        rs.getString("by_variant"), rs.getString("evidence")))
                .optional();
        if (head.isEmpty()) {
            return Optional.empty();
        }
        TestSummary s = head.get().summary();

        List<TestDetail.VariantStatus> variants = new ArrayList<>();
        Json.parseObject(head.get().byVariant())
                .forEach((variant, status) -> variants.add(new TestDetail.VariantStatus(variant, String.valueOf(status))));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> evidence = Json.parseArray(head.get().evidence()).stream()
                .map(e -> (Map<String, Object>) e)
                .toList();

        // The last 50 final outcomes, oldest first
        List<TestDetail.TimelinePoint> timeline = jdbc.sql("""
                select * from (
                  select te.run_id, te.executed_at, te.status, r.commit_sha, r.branch, u.variant, te.upload_id,
                         exists (select 1 from test_executions prior
                                  where prior.upload_id = te.upload_id and prior.test_case_id = te.test_case_id
                                    and prior.execution_index < te.execution_index
                                    and prior.status in ('FAILED', 'ERRORED')) as retried
                    from test_executions te
                    join ci_runs r on r.id = te.run_id
                    join uploads u on u.id = te.upload_id
                   where te.test_case_id = :id and te.project_id = :project and te.is_final
                   order by te.executed_at desc, te.upload_id desc
                   limit 50
                ) last50
                order by executed_at, upload_id
                """)
                .param("id", testId)
                .param("project", projectId)
                .query((rs, i) -> new TestDetail.TimelinePoint(
                        rs.getLong("run_id"),
                        Db.instant(rs, "executed_at"),
                        outcome(rs.getString("status"), rs.getBoolean("retried")),
                        rs.getString("commit_sha"),
                        rs.getString("branch"),
                        rs.getString("variant")))
                .list();

        List<TestDetail.FailureGroupRef> groups = jdbc.sql("""
                select fg.id, fg.failure_type, fg.normalized_message, fg.occurrences
                  from failure_groups fg
                 where fg.project_id = :project
                   and fg.id in (select distinct failure_group_id from test_executions
                                  where test_case_id = :id and failure_group_id is not null)
                 order by fg.last_seen_at desc
                """)
                .param("project", projectId)
                .param("id", testId)
                .query((rs, i) -> new TestDetail.FailureGroupRef(
                        rs.getLong("id"), rs.getString("failure_type"), rs.getString("normalized_message"),
                        rs.getLong("occurrences")))
                .list();

        List<TestDetail.DurationPoint> durations = jdbc.sql("""
                select day, sum(total_duration_ms) / greatest(sum(passed + failed + skipped), 1) as average_ms
                  from test_daily_stats
                 where test_case_id = :id and day >= (select max(day) from test_daily_stats where test_case_id = :id) - 29
                 group by day
                 order by day
                """)
                .param("id", testId)
                .query((rs, i) -> new TestDetail.DurationPoint(
                        rs.getObject("day", LocalDate.class), rs.getLong("average_ms")))
                .list();

        return Optional.of(new TestDetail(
                s.id(), s.testKey(), s.suite(), s.name(), s.status(), s.statusSince(), s.impact(), s.flipRate(),
                s.lastFailureAt(), s.quarantined(), head.get().methodKey(), head.get().file(), variants, evidence,
                timeline, groups, durations, null, false));
    }

    public Optional<PageRequest.Page<ExecutionView>> executions(long projectId, long testId, PageRequest page) {
        boolean exists = jdbc.sql("select count(*) from test_cases where id = :id and project_id = :project")
                .param("id", testId)
                .param("project", projectId)
                .query(Long.class)
                .single() > 0;
        if (!exists) {
            return Optional.empty();
        }
        List<ExecutionView> rows = jdbc.sql("""
                select te.id, te.run_id, te.upload_id, te.execution_index, te.is_final, te.status, te.quarantined,
                       te.duration_ms, te.executed_at, r.commit_sha, r.branch, u.variant, te.failure_group_id,
                       te.failure_type, te.failure_message, te.stack_trace
                  from test_executions te
                  join ci_runs r on r.id = te.run_id
                  join uploads u on u.id = te.upload_id
                 where te.test_case_id = :id and te.project_id = :project
                 order by te.executed_at desc, te.upload_id desc, te.execution_index desc
                 offset :offset limit :limit
                """)
                .param("id", testId)
                .param("project", projectId)
                .param("offset", page.offset())
                .param("limit", page.fetchSize())
                .query((rs, i) -> new ExecutionView(
                        rs.getLong("id"),
                        rs.getLong("run_id"),
                        rs.getLong("upload_id"),
                        rs.getInt("execution_index"),
                        rs.getBoolean("is_final"),
                        rs.getString("status"),
                        rs.getBoolean("quarantined"),
                        rs.getInt("duration_ms"),
                        Db.instant(rs, "executed_at"),
                        rs.getString("commit_sha"),
                        rs.getString("branch"),
                        rs.getString("variant"),
                        Db.nullableLong(rs, "failure_group_id"),
                        rs.getString("failure_type"),
                        rs.getString("failure_message"),
                        rs.getString("stack_trace")))
                .list();
        return Optional.of(page.toPage(rows));
    }

    static String outcome(String status, boolean retried) {
        return switch (status) {
            case "PASSED" -> retried ? "PASSED_ON_RETRY" : "PASSED";
            case "SKIPPED" -> "SKIPPED";
            default -> "FAILED";
        };
    }

    static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    public static TestSummary summary(ResultSet rs, int row) throws SQLException {
        Instant lastFailure = Db.instant(rs, "last_failure_at");
        return new TestSummary(
                rs.getLong("id"),
                rs.getString("test_key"),
                rs.getString("suite"),
                rs.getString("name"),
                rs.getString("status"),
                Db.instant(rs, "status_since"),
                rs.getInt("impact"),
                rs.getDouble("flip_rate"),
                lastFailure,
                false);
    }
}
