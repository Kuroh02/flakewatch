package io.github.chakrabartyaditi2002.flakewatch.jobs;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Queues the once-a-day jobs: aging re-check (FR-DET-010) and retention purges (FR-ING-012, NFR-DATA-001).
 *
 * <p>The free host sleeps when idle, so a timer can't be trusted to fire. Instead this runs at
 * startup and whenever an upload arrives. Date-based keys make each job run at most once a day.
 */
@Component
public class DailyJobs {

    private final JobQueue queue;
    private final JobWakeup wakeup;
    private final Clock clock;
    private volatile LocalDate lastQueued;

    public DailyJobs(JobQueue queue, JobWakeup wakeup, Clock clock) {
        this.queue = queue;
        this.wakeup = wakeup;
        this.clock = clock;
    }

    public void ensureToday() {
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        if (today.equals(lastQueued)) {
            return;
        }
        Map<String, Object> payload = Map.of("day", today.toString());
        boolean queued = queue.enqueueOnce(JobKinds.RECLASSIFY_AGING, payload, "aging:" + today, now);
        queued |= queue.enqueueOnce(JobKinds.PURGE_RAW_FILES, payload, "purge-raw:" + today, now);
        queued |= queue.enqueueOnce(JobKinds.PURGE_OLD_DATA, payload, "purge-old:" + today, now);
        lastQueued = today;
        if (queued) {
            wakeup.signal();
        }
    }
}
