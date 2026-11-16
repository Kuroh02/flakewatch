package io.github.chakrabartyaditi2002.flakewatch.detection.internal;

import io.github.chakrabartyaditi2002.flakewatch.core.detection.Classification;
import io.github.chakrabartyaditi2002.flakewatch.core.detection.DetectionSettings;
import io.github.chakrabartyaditi2002.flakewatch.core.detection.Evidence;
import io.github.chakrabartyaditi2002.flakewatch.core.detection.Execution;
import io.github.chakrabartyaditi2002.flakewatch.core.report.ExecutionStatus;
import io.github.chakrabartyaditi2002.flakewatch.platform.db.Db;
import io.github.chakrabartyaditi2002.flakewatch.platform.json.Json;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.support.SqlArrayValue;
import org.springframework.stereotype.Repository;

/** Reads executions for the classifier and writes statuses, events and daily snapshots. */
@Repository
public class StatusRepository {

    private final JdbcClient jdbc;

    public StatusRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Tests from an upload that are new, not Stable, or failed recently (detection rules §11). */
    public List<Long> needingRecheck(Set<Long> testIds, Instant windowStart) {
        return jdbc.sql("""
                select tc.id from test_cases tc
                  left join test_status ts on ts.test_case_id = tc.id
                 where tc.id = any(:ids)
                   and (ts.test_case_id is null or ts.status <> 'STABLE' or ts.last_failure_at >= :windowStart)
                """)
                .param("ids", new SqlArrayValue("bigint", testIds.toArray()))
                .param("windowStart", Db.ts(windowStart))
                .query(Long.class)
                .list();
    }

    /** At most the 200 most recent executions per test and variant, inside the window (detection rules §10). */
    public Map<Long, List<Execution>> executionsInWindow(Collection<Long> testIds, Instant windowStart) {
        Map<Long, List<Execution>> out = new HashMap<>();
        jdbc.sql("""
                select * from (
                  select te.test_case_id, r.provider, r.external_run_id, r.attempt, u.job_key, u.variant, r.branch,
                         r.commit_sha, te.execution_index, te.status, te.executed_at, te.run_id,
                         row_number() over (partition by te.test_case_id, u.variant
                                            order by te.executed_at desc, te.upload_id desc, te.execution_index desc) as n
                    from test_executions te
                    join uploads u on u.id = te.upload_id
                    join ci_runs r on r.id = te.run_id
                   where te.test_case_id = any(:ids) and te.executed_at >= :windowStart
                ) recent
                where n <= :max
                """)
                .param("ids", new SqlArrayValue("bigint", testIds.toArray()))
                .param("windowStart", Db.ts(windowStart))
                .param("max", DetectionSettings.MAX_EXECUTIONS_PER_VARIANT)
                .query((rs, i) -> Map.entry(
                        rs.getLong("test_case_id"),
                        new Execution(
                                rs.getString("provider"),
                                rs.getString("external_run_id"),
                                rs.getInt("attempt"),
                                rs.getString("job_key"),
                                rs.getString("variant"),
                                rs.getString("branch"),
                                rs.getString("commit_sha"),
                                rs.getInt("execution_index"),
                                ExecutionStatus.valueOf(rs.getString("status")),
                                Db.instant(rs, "executed_at"),
                                rs.getLong("run_id"))))
                .list()
                .forEach(e -> out.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(e.getValue()));
        return out;
    }

    public Map<Long, String> currentStatuses(Collection<Long> testIds) {
        Map<Long, String> out = new HashMap<>();
        jdbc.sql("select test_case_id, status from test_status where test_case_id = any(:ids)")
                .param("ids", new SqlArrayValue("bigint", testIds.toArray()))
                .query((rs, i) -> Map.entry(rs.getLong("test_case_id"), rs.getString("status")))
                .list()
                .forEach(e -> out.put(e.getKey(), e.getValue()));
        return out;
    }

    public void save(long projectId, long testId, Classification c, Instant now) {
        Map<String, Object> byVariant = new LinkedHashMap<>();
        c.byVariant().forEach((variant, status) -> byVariant.put(variant, status.name()));
        jdbc.sql("""
                insert into test_status (test_case_id, project_id, status, status_since, by_variant, evidence, impact,
                                         flip_rate, last_failure_at, computed_at)
                values (:test, :project, :status, :now, cast(:byVariant as jsonb), cast(:evidence as jsonb), :impact,
                        :flipRate, :lastFailure, :now)
                on conflict (test_case_id) do update
                   set status = excluded.status,
                       status_since = case when test_status.status = excluded.status
                                           then test_status.status_since else excluded.status_since end,
                       by_variant = excluded.by_variant,
                       evidence = excluded.evidence,
                       impact = excluded.impact,
                       flip_rate = excluded.flip_rate,
                       last_failure_at = excluded.last_failure_at,
                       computed_at = excluded.computed_at
                """)
                .param("test", testId)
                .param("project", projectId)
                .param("status", c.overall().name())
                .param("now", Db.ts(now))
                .param("byVariant", Json.write(byVariant))
                .param("evidence", Json.write(evidenceJson(c.evidence())))
                .param("impact", c.impact())
                .param("flipRate", Math.round(c.flipRate() * 10_000) / 10_000.0)
                .param("lastFailure", Db.ts(c.lastFailureAt()))
                .update();
    }

    public void recordChange(long projectId, long testId, String from, String to, String reason, Instant now) {
        jdbc.sql("""
                insert into test_status_events (project_id, test_case_id, from_status, to_status, reason, occurred_at)
                values (:project, :test, :from, :to, :reason, :now)
                """)
                .param("project", projectId)
                .param("test", testId)
                .param("from", from)
                .param("to", to)
                .param("reason", reason)
                .param("now", Db.ts(now))
                .update();
    }

    public List<Long> testIds(long projectId) {
        return jdbc.sql("select id from test_cases where project_id = :project order by id")
                .param("project", projectId)
                .query(Long.class)
                .list();
    }

    public List<Long> projectIds() {
        return jdbc.sql("select id from projects order by id").query(Long.class).list();
    }

    public void snapshot(long projectId, LocalDate day) {
        jdbc.sql("""
                insert into project_daily_snapshots (project_id, day, stable, flaky, suspected, failing, broken, not_enough_data)
                select :project, :day,
                       count(*) filter (where status = 'STABLE'),
                       count(*) filter (where status = 'FLAKY'),
                       count(*) filter (where status = 'SUSPECTED'),
                       count(*) filter (where status = 'FAILING'),
                       count(*) filter (where status = 'BROKEN'),
                       count(*) filter (where status = 'NOT_ENOUGH_DATA')
                  from test_status where project_id = :project
                on conflict (project_id, day) do update
                   set stable = excluded.stable, flaky = excluded.flaky, suspected = excluded.suspected,
                       failing = excluded.failing, broken = excluded.broken, not_enough_data = excluded.not_enough_data
                """)
                .param("project", projectId)
                .param("day", day)
                .update();
    }

    static List<Map<String, Object>> evidenceJson(List<Evidence> evidence) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Evidence e : evidence) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("kind", e.kind().name());
            m.put("runIds", e.runIds());
            m.put("commitSha", e.commitSha());
            m.put("variant", e.variant());
            m.put("observedAt", e.observedAt());
            m.put("explanation", e.explanation());
            out.add(m);
        }
        return out;
    }
}
