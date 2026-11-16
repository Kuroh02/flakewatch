package io.github.chakrabartyaditi2002.flakewatch.jobs;

import io.github.chakrabartyaditi2002.flakewatch.platform.db.Db;
import io.github.chakrabartyaditi2002.flakewatch.platform.json.Json;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The background job queue: a PostgreSQL table with leases and fencing tokens (ADR-0003, PLAN §8.3).
 *
 * <p>Methods that change data join the caller's transaction, so "save the upload" and "queue its
 * job" commit together.
 */
@Repository
public class JobQueue {

    public static final int DEFAULT_MAX_ATTEMPTS = 5;

    private final JdbcClient jdbc;

    public JobQueue(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Queues a job. With a dedupe key, a second identical job is ignored while the first is pending or running. */
    public void enqueue(String kind, Map<String, ?> payload, String dedupeKey, Instant now) {
        jdbc.sql("""
                insert into jobs (kind, payload, dedupe_key, status, attempts, max_attempts, run_after, created_at, updated_at)
                values (:kind, cast(:payload as jsonb), :dedupe, 'PENDING', 0, :max, :now, :now, :now)
                on conflict do nothing
                """)
                .param("kind", kind)
                .param("payload", Json.write(payload))
                .param("dedupe", dedupeKey)
                .param("max", DEFAULT_MAX_ATTEMPTS)
                .param("now", Db.ts(now))
                .update();
    }

    /**
     * Queues a job only if no job with this key has ever existed (finished jobs count too).
     * Used for daily jobs, keyed by date, so they run once a day however often this is called.
     */
    public boolean enqueueOnce(String kind, Map<String, ?> payload, String dedupeKey, Instant now) {
        int rows = jdbc.sql("""
                insert into jobs (kind, payload, dedupe_key, status, attempts, max_attempts, run_after, created_at, updated_at)
                select :kind, cast(:payload as jsonb), :dedupe, 'PENDING', 0, :max, :now, :now, :now
                 where not exists (select 1 from jobs where dedupe_key = :dedupe)
                on conflict do nothing
                """)
                .param("kind", kind)
                .param("payload", Json.write(payload))
                .param("dedupe", dedupeKey)
                .param("max", DEFAULT_MAX_ATTEMPTS)
                .param("now", Db.ts(now))
                .update();
        return rows > 0;
    }

    /** Claims the next due job, or a running job whose lease has expired. Its own short transaction. */
    public Optional<Job> claim(Instant now, Duration lease, UUID token) {
        return jdbc.sql("""
                with next as (
                  select id from jobs
                   where (status = 'PENDING' and run_after <= :now)
                      or (status = 'RUNNING' and lease_expires_at <= :now)
                   order by run_after, id
                   limit 1
                   for update skip locked
                )
                update jobs j
                   set status = 'RUNNING',
                       attempts = j.attempts + 1,
                       lease_token = :token,
                       lease_expires_at = :leaseUntil,
                       updated_at = :now
                  from next
                 where j.id = next.id
                returning j.id, j.kind, j.payload::text as payload, j.attempts, j.max_attempts, j.lease_token
                """)
                .param("now", Db.ts(now))
                .param("token", token)
                .param("leaseUntil", Db.ts(now.plus(lease)))
                .query((rs, i) -> new Job(
                        rs.getLong("id"),
                        rs.getString("kind"),
                        Json.parseObject(rs.getString("payload")),
                        rs.getInt("attempts"),
                        rs.getInt("max_attempts"),
                        rs.getObject("lease_token", UUID.class)))
                .optional();
    }

    /** The last statement of a job's transaction. False means another worker owns the job now. */
    public boolean complete(Job job, Instant now) {
        return jdbc.sql("""
                update jobs set status = 'DONE', finished_at = :now, updated_at = :now, lease_token = null
                 where id = :id and lease_token = :token and status = 'RUNNING'
                """)
                .param("now", Db.ts(now))
                .param("id", job.id())
                .param("token", job.leaseToken())
                .update() == 1;
    }

    /** After a failed attempt: retry later, or give up when attempts are used up. Returns true when the job is now DEAD. */
    public boolean fail(Job job, String error, Instant now, Duration backoff) {
        return jdbc.sql("""
                update jobs
                   set status = case when attempts >= max_attempts then 'DEAD' else 'PENDING' end,
                       run_after = :retryAt,
                       finished_at = case when attempts >= max_attempts then cast(:now as timestamptz) end,
                       last_error = :error, lease_token = null, lease_expires_at = null, updated_at = :now
                 where id = :id and lease_token = :token
                returning status
                """)
                .param("retryAt", Db.ts(now.plus(backoff)))
                .param("now", Db.ts(now))
                .param("error", truncate(error))
                .param("id", job.id())
                .param("token", job.leaseToken())
                .query(String.class)
                .optional()
                .map("DEAD"::equals)
                .orElse(false);
    }

    /** A job claimed more often than allowed (it keeps crashing the process) is dead without running again. */
    public void markDead(Job job, String error, Instant now) {
        jdbc.sql("""
                update jobs set status = 'DEAD', finished_at = :now, updated_at = :now, last_error = :error,
                       lease_token = null, lease_expires_at = null
                 where id = :id and lease_token = :token
                """)
                .param("now", Db.ts(now))
                .param("error", truncate(error))
                .param("id", job.id())
                .param("token", job.leaseToken())
                .update();
    }

    /** On shutdown: hand the job back so it runs right after restart. This attempt doesn't count. */
    public void release(Job job, Instant now) {
        jdbc.sql("""
                update jobs set status = 'PENDING', attempts = greatest(attempts - 1, 0), run_after = :now,
                       lease_token = null, lease_expires_at = null, updated_at = :now
                 where id = :id and lease_token = :token and status = 'RUNNING'
                """)
                .param("now", Db.ts(now))
                .param("id", job.id())
                .param("token", job.leaseToken())
                .update();
    }

    /** When the next job becomes due (a pending job or an expiring lease), or empty when there is no work at all. */
    public Optional<Instant> nextDueAt() {
        return jdbc.sql("""
                select least(
                  (select min(run_after) from jobs where status = 'PENDING'),
                  (select min(lease_expires_at) from jobs where status = 'RUNNING')) as due
                """)
                .query((rs, i) -> Optional.ofNullable(Db.instant(rs, "due")))
                .single();
    }

    private static String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() > 2000 ? error.substring(0, 2000) : error;
    }
}
