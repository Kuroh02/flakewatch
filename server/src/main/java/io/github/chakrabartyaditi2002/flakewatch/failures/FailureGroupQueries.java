package io.github.chakrabartyaditi2002.flakewatch.failures;

import io.github.chakrabartyaditi2002.flakewatch.platform.db.Db;
import io.github.chakrabartyaditi2002.flakewatch.platform.db.PageRequest;
import io.github.chakrabartyaditi2002.flakewatch.tests.TestQueries;
import io.github.chakrabartyaditi2002.flakewatch.tests.TestSummary;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Read side for failure groups (FR-GRP-003, FR-UI-005). */
@Component
public class FailureGroupQueries {

    private static final String SELECT = """
            select fg.id, fg.failure_type, fg.normalized_message, fg.sample_message, fg.top_frames,
                   fg.first_seen_at, fg.last_seen_at, fg.occurrences,
                   (select count(distinct te.test_case_id) from test_executions te
                     where te.failure_group_id = fg.id) as affected_tests
              from failure_groups fg
            """;

    private final JdbcClient jdbc;

    public FailureGroupQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public PageRequest.Page<FailureGroupView> list(long projectId, PageRequest page) {
        List<FailureGroupView> rows = jdbc.sql(SELECT + """
                 where fg.project_id = :project
                 order by fg.last_seen_at desc, fg.id desc
                 offset :offset limit :limit
                """)
                .param("project", projectId)
                .param("offset", page.offset())
                .param("limit", page.fetchSize())
                .query((rs, i) -> map(rs, null))
                .list();
        return page.toPage(rows);
    }

    public Optional<FailureGroupView> detail(long projectId, long groupId) {
        boolean exists = jdbc.sql("select count(*) from failure_groups where id = :id and project_id = :project")
                .param("id", groupId)
                .param("project", projectId)
                .query(Long.class)
                .single() > 0;
        if (!exists) {
            return Optional.empty();
        }
        List<TestSummary> tests = jdbc.sql("select " + TestQueries.SUMMARY_COLUMNS + """
                  from test_cases tc
                  left join test_status ts on ts.test_case_id = tc.id
                 where tc.project_id = :project
                   and tc.id in (select distinct test_case_id from test_executions where failure_group_id = :group)
                 order by coalesce(ts.impact, 0) desc, tc.test_key
                 limit 100
                """)
                .param("project", projectId)
                .param("group", groupId)
                .query(TestQueries::summary)
                .list();
        return jdbc.sql(SELECT + " where fg.project_id = :project and fg.id = :id")
                .param("project", projectId)
                .param("id", groupId)
                .query((rs, i) -> map(rs, tests))
                .optional();
    }

    private static FailureGroupView map(ResultSet rs, List<TestSummary> tests) throws SQLException {
        Array frames = rs.getArray("top_frames");
        List<String> topFrames = frames == null ? List.of() : Arrays.asList((String[]) frames.getArray());
        return new FailureGroupView(
                rs.getLong("id"),
                rs.getString("failure_type"),
                rs.getString("normalized_message"),
                rs.getString("sample_message"),
                topFrames,
                Db.instant(rs, "first_seen_at"),
                Db.instant(rs, "last_seen_at"),
                rs.getLong("occurrences"),
                rs.getInt("affected_tests"),
                tests);
    }
}
