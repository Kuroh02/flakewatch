package io.github.chakrabartyaditi2002.flakewatch.jobs;

import io.github.chakrabartyaditi2002.flakewatch.platform.db.Db;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Deletes expired executions, daily statistics and finished jobs (NFR-DATA-001, data model §5). */
@Component
public class PurgeOldDataHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(PurgeOldDataHandler.class);
    private static final int BATCH = 5000;

    private final JdbcClient jdbc;
    private final Clock clock;
    private final int executionDays;
    private final int statsDays;

    public PurgeOldDataHandler(
            JdbcClient jdbc,
            Clock clock,
            @Value("${flakewatch.retention.executions-days:90}") int executionDays,
            @Value("${flakewatch.retention.daily-stats-days:400}") int statsDays) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.executionDays = executionDays;
        this.statsDays = statsDays;
    }

    @Override
    public String kind() {
        return JobKinds.PURGE_OLD_DATA;
    }

    @Override
    public void handle(Job job) {
        Instant now = clock.instant();
        Instant executionCutoff = now.minus(Duration.ofDays(executionDays));
        int executions = 0;
        int batch;
        do {
            batch = jdbc.sql("""
                    delete from test_executions
                     where id in (select id from test_executions where executed_at < :cutoff limit :batch)
                    """)
                    .param("cutoff", Db.ts(executionCutoff))
                    .param("batch", BATCH)
                    .update();
            executions += batch;
        } while (batch == BATCH);

        LocalDate statsCutoff = LocalDate.ofInstant(now, ZoneOffset.UTC).minusDays(statsDays);
        int stats = jdbc.sql("delete from test_daily_stats where day < :cutoff").param("cutoff", statsCutoff).update();

        int jobs = jdbc.sql("""
                delete from jobs
                 where (status = 'DONE' and finished_at < :doneCutoff)
                    or (status = 'DEAD' and finished_at < :deadCutoff)
                """)
                .param("doneCutoff", Db.ts(now.minus(Duration.ofDays(7))))
                .param("deadCutoff", Db.ts(now.minus(Duration.ofDays(30))))
                .update();
        log.info("retention.old-data executions={} dailyStats={} jobs={}", executions, stats, jobs);
    }
}
