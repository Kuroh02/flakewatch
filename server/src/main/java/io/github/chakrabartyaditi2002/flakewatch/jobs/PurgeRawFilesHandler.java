package io.github.chakrabartyaditi2002.flakewatch.jobs;

import io.github.chakrabartyaditi2002.flakewatch.platform.db.Db;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Deletes raw uploaded files after 7 days (FR-ING-012). The parsed results stay. */
@Component
public class PurgeRawFilesHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(PurgeRawFilesHandler.class);

    private final JdbcClient jdbc;
    private final Clock clock;
    private final int days;

    public PurgeRawFilesHandler(
            JdbcClient jdbc, Clock clock, @Value("${flakewatch.retention.raw-files-days:7}") int days) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.days = days;
    }

    @Override
    public String kind() {
        return JobKinds.PURGE_RAW_FILES;
    }

    @Override
    public void handle(Job job) {
        Instant now = clock.instant();
        int purged = jdbc.sql("""
                update upload_files set content_gzip = null, purged_at = :now
                 where content_gzip is not null
                   and upload_id in (select id from uploads where received_at < :cutoff)
                """)
                .param("now", Db.ts(now))
                .param("cutoff", Db.ts(now.minus(Duration.ofDays(days))))
                .update();
        log.info("retention.raw-files purged={}", purged);
    }
}
