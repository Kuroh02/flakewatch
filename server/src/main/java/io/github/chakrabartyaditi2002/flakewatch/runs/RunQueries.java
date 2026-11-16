package io.github.chakrabartyaditi2002.flakewatch.runs;

import io.github.chakrabartyaditi2002.flakewatch.platform.db.Db;
import io.github.chakrabartyaditi2002.flakewatch.platform.db.PageRequest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Read side for CI runs (FR-UI-004). Every query is scoped by project ID. */
@Component
public class RunQueries {

    private static final String SUMMARY_SELECT = """
            select r.id, r.provider, r.external_run_id, r.attempt, r.commit_sha, r.branch, r.pull_request,
                   r.started_at, r.repository_url,
                   coalesce(sum((u.summary->>'tests')::int), 0)   as tests,
                   coalesce(sum((u.summary->>'passed')::int), 0)  as passed,
                   coalesce(sum((u.summary->>'failed')::int), 0)  as failed,
                   coalesce(sum((u.summary->>'errored')::int), 0) as errored,
                   coalesce(sum((u.summary->>'skipped')::int), 0) as skipped,
                   coalesce(sum((u.summary->>'retried')::int), 0) as retried
              from ci_runs r
              left join uploads u on u.run_id = r.id
            """;

    private final JdbcClient jdbc;

    public RunQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public PageRequest.Page<RunSummary> list(long projectId, String branch, PageRequest page) {
        List<RunSummary> rows = jdbc.sql(SUMMARY_SELECT + """
                 where r.project_id = :project and (cast(:branch as text) is null or r.branch = :branch)
                 group by r.id
                 order by r.started_at desc, r.id desc
                 offset :offset limit :limit
                """)
                .param("project", projectId)
                .param("branch", branch)
                .param("offset", page.offset())
                .param("limit", page.fetchSize())
                .query(RunQueries::summary)
                .list();
        return page.toPage(rows);
    }

    public List<RunSummary> recent(long projectId, int limit) {
        return jdbc.sql(SUMMARY_SELECT + """
                 where r.project_id = :project
                 group by r.id
                 order by r.started_at desc, r.id desc
                 limit :limit
                """)
                .param("project", projectId)
                .param("limit", limit)
                .query(RunQueries::summary)
                .list();
    }

    public Optional<RunDetail> detail(long projectId, long runId) {
        record Head(RunSummary summary, String repositoryUrl) {}
        Optional<Head> head = jdbc.sql(SUMMARY_SELECT + " where r.project_id = :project and r.id = :run group by r.id")
                .param("project", projectId)
                .param("run", runId)
                .query((rs, i) -> new Head(summary(rs, i), rs.getString("repository_url")))
                .optional();
        if (head.isEmpty()) {
            return Optional.empty();
        }
        List<RunDetail.UploadRef> uploads = jdbc.sql("""
                select id, job_key, variant, status from uploads where run_id = :run order by job_key, id
                """)
                .param("run", runId)
                .query((rs, i) -> new RunDetail.UploadRef(
                        rs.getLong("id"), rs.getString("job_key"), rs.getString("variant"), rs.getString("status")))
                .list();
        // Every test whose final outcome failed in this run, plus tests that passed only on a retry
        List<RunDetail.RunFailure> failures = jdbc.sql("""
                select te.test_case_id, tc.test_key, te.status as final_status, te.quarantined, te.failure_group_id,
                       te.failure_message, u.variant, u.job_key,
                       coalesce(ts.status, 'NOT_ENOUGH_DATA') as test_status,
                       exists (select 1 from test_executions prior
                                where prior.upload_id = te.upload_id and prior.test_case_id = te.test_case_id
                                  and prior.execution_index < te.execution_index
                                  and prior.status in ('FAILED', 'ERRORED')) as had_earlier_failure
                  from test_executions te
                  join uploads u on u.id = te.upload_id
                  join test_cases tc on tc.id = te.test_case_id
                  left join test_status ts on ts.test_case_id = te.test_case_id
                 where te.run_id = :run and te.project_id = :project and te.is_final
                   and (te.status in ('FAILED', 'ERRORED')
                        or exists (select 1 from test_executions prior
                                    where prior.upload_id = te.upload_id and prior.test_case_id = te.test_case_id
                                      and prior.execution_index < te.execution_index
                                      and prior.status in ('FAILED', 'ERRORED')))
                 order by tc.test_key, u.job_key
                """)
                .param("run", runId)
                .param("project", projectId)
                .query((rs, i) -> new RunDetail.RunFailure(
                        rs.getLong("test_case_id"),
                        rs.getString("test_key"),
                        label(rs.getString("final_status"), rs.getBoolean("quarantined"),
                                rs.getBoolean("had_earlier_failure"), rs.getString("test_status")),
                        rs.getString("test_status"),
                        Db.nullableLong(rs, "failure_group_id"),
                        firstLine(rs.getString("failure_message")),
                        rs.getString("variant"),
                        rs.getString("job_key")))
                .list();
        return Optional.of(RunDetail.of(head.get().summary(), head.get().repositoryUrl(), uploads, failures));
    }

    /** "Is this failure real?" (US-02). */
    static String label(String finalStatus, boolean quarantined, boolean hadEarlierFailure, String testStatus) {
        if (finalStatus.equals("PASSED") && hadEarlierFailure) {
            return "PASSED_ON_RETRY";
        }
        if (quarantined) {
            return "QUARANTINED";
        }
        return switch (testStatus) {
            case "FLAKY" -> "KNOWN_FLAKY";
            case "BROKEN" -> "BROKEN";
            default -> "NEW_FAILURE";
        };
    }

    private static String firstLine(String message) {
        if (message == null) {
            return null;
        }
        int nl = message.indexOf('\n');
        String line = nl >= 0 ? message.substring(0, nl) : message;
        return line.length() > 300 ? line.substring(0, 300) : line;
    }

    static RunSummary summary(ResultSet rs, int row) throws SQLException {
        Map<String, Integer> totals = new LinkedHashMap<>();
        for (String key : List.of("tests", "passed", "failed", "errored", "skipped", "retried")) {
            totals.put(key, rs.getInt(key));
        }
        return new RunSummary(
                rs.getLong("id"),
                rs.getString("provider"),
                rs.getString("external_run_id"),
                rs.getInt("attempt"),
                rs.getString("commit_sha"),
                rs.getString("branch"),
                Db.nullableInt(rs, "pull_request"),
                Db.instant(rs, "started_at"),
                totals,
                rs.getInt("failed") + rs.getInt("errored"));
    }
}
