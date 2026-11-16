package io.github.chakrabartyaditi2002.flakewatch.jobs;

import io.github.chakrabartyaditi2002.flakewatch.platform.random.RandomSource;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs queued jobs (PLAN §8).
 *
 * <ul>
 *   <li>A handler's work and the job's completion commit in one transaction, guarded by the
 *       fencing token. A worker that lost its lease can't commit anything.
 *   <li>Failures are retried with exponential backoff and jitter; after 5 attempts the job is DEAD.
 *   <li>Idle workers sleep until an upload wakes them, or until the next retry is due. With no
 *       work at all they don't touch the database, so it can sleep (NFR-COST-001).
 *   <li>Tests set {@code flakewatch.workers=0} and drive jobs with {@link #runUntilIdle()}.
 * </ul>
 */
@Component
public class JobWorker implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(JobWorker.class);

    static final Duration LEASE = Duration.ofMinutes(5);
    static final Duration MAX_IDLE_WAIT = Duration.ofMinutes(10);
    static final Duration MIN_IDLE_WAIT = Duration.ofSeconds(1);
    static final Duration SHUTDOWN_GRACE = Duration.ofSeconds(25);

    private final JobQueue queue;
    private final Map<String, JobHandler> handlers = new HashMap<>();
    private final TransactionTemplate tx;
    private final Clock clock;
    private final RandomSource random;
    private final JobWakeup wakeup;
    private final DailyJobs dailyJobs;
    private final int workerCount;
    private final List<Thread> threads = new ArrayList<>();
    private volatile boolean running;

    public JobWorker(
            JobQueue queue,
            List<JobHandler> handlerBeans,
            PlatformTransactionManager transactionManager,
            Clock clock,
            RandomSource random,
            JobWakeup wakeup,
            DailyJobs dailyJobs,
            @Value("${flakewatch.workers:2}") int workerCount) {
        this.queue = queue;
        for (JobHandler handler : handlerBeans) {
            handlers.put(handler.kind(), handler);
        }
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.random = random;
        this.wakeup = wakeup;
        this.dailyJobs = dailyJobs;
        this.workerCount = workerCount;
    }

    /** Claims and runs one job. Returns false when no job is due. */
    public boolean runOnce() {
        Instant now = clock.instant();
        Optional<Job> claimed = tx.execute(status -> queue.claim(now, LEASE, random.nextUuid()));
        if (claimed == null || claimed.isEmpty()) {
            return false;
        }
        Job job = claimed.get();
        MDC.put("jobId", Long.toString(job.id()));
        try {
            run(job);
        } finally {
            MDC.remove("jobId");
        }
        return true;
    }

    /** Runs jobs until none is due. Tests use this instead of background threads. */
    public int runUntilIdle() {
        int ran = 0;
        while (runOnce()) {
            ran++;
            if (ran > 100_000) {
                throw new IllegalStateException("runUntilIdle ran more than 100,000 jobs; is a job re-queueing itself?");
            }
        }
        return ran;
    }

    private void run(Job job) {
        JobHandler handler = handlers.get(job.kind());
        if (job.attempts() > job.maxAttempts() || handler == null) {
            String reason = handler == null ? "no handler for job kind " + job.kind() : "attempts used up";
            tx.executeWithoutResult(status -> queue.markDead(job, reason, clock.instant()));
            log.error("job.dead kind={} attempts={} reason={}", job.kind(), job.attempts(), reason);
            if (handler != null) {
                tx.executeWithoutResult(status -> handler.onDead(job, "Processing failed repeatedly"));
            }
            return;
        }
        log.info("job.claimed kind={} attempt={}", job.kind(), job.attempts());
        long started = System.nanoTime();
        try {
            tx.executeWithoutResult(status -> {
                handler.handle(job);
                if (!queue.complete(job, clock.instant())) {
                    throw new LeaseLostException(job.id());
                }
            });
            log.info("job.done kind={} durationMs={}", job.kind(), (System.nanoTime() - started) / 1_000_000);
        } catch (LeaseLostException e) {
            log.warn("job.lease-lost kind={}: another worker owns it now; our work was rolled back", job.kind());
        } catch (RuntimeException e) {
            if (!running && workerCount > 0) {
                // shutting down: hand the job back so it runs right after restart (NFR-REL-003)
                tx.executeWithoutResult(status -> queue.release(job, clock.instant()));
                log.info("job.released kind={} because the worker is shutting down", job.kind());
                return;
            }
            String error = describe(e);
            Duration backoff = backoff(job.attempts());
            Boolean dead = tx.execute(status -> queue.fail(job, error, clock.instant(), backoff));
            if (Boolean.TRUE.equals(dead)) {
                log.error("job.dead kind={} attempts={} error={}", job.kind(), job.attempts(), error, e);
                tx.executeWithoutResult(status -> handler.onDead(job, error));
            } else {
                log.warn("job.failed kind={} attempt={} retryInSeconds={} error={}",
                        job.kind(), job.attempts(), backoff.toSeconds(), error, e);
            }
        }
    }

    /** 30 s × 2^(attempts−1), at most 1 hour, ±20% jitter (PLAN §8.3). */
    Duration backoff(int attempts) {
        double base = Math.min(3600, 30 * Math.pow(2, Math.max(0, attempts - 1)));
        double jitter = random.nextDouble(0.8, 1.2);
        return Duration.ofMillis(Math.round(base * jitter * 1000));
    }

    private static String describe(Throwable e) {
        String message = e.getMessage();
        return e.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    // --- background threads ----------------------------------------------------------------------

    @Override
    public void start() {
        if (workerCount <= 0 || running) {
            return;
        }
        running = true;
        try {
            dailyJobs.ensureToday();
        } catch (RuntimeException e) {
            log.warn("Could not queue today's daily jobs at startup: {}", e.getMessage());
        }
        for (int i = 0; i < workerCount; i++) {
            Thread t = new Thread(this::loop, "job-worker-" + i);
            t.setDaemon(true);
            threads.add(t);
            t.start();
        }
        log.info("Started {} job worker(s)", workerCount);
    }

    private void loop() {
        while (running) {
            long generation = wakeup.generation();
            boolean worked;
            try {
                worked = runOnce();
            } catch (RuntimeException e) {
                log.error("job worker error: {}", e.getMessage(), e);
                worked = false;
            }
            if (worked) {
                continue;
            }
            try {
                wakeup.await(generation, idleWait());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /** How long an idle worker sleeps. Null means "until woken": there is no work at all. */
    private Duration idleWait() {
        try {
            Optional<Instant> due = queue.nextDueAt();
            if (due.isEmpty()) {
                return null;
            }
            Duration wait = Duration.between(clock.instant(), due.get());
            if (wait.compareTo(MIN_IDLE_WAIT) < 0) {
                return MIN_IDLE_WAIT;
            }
            return wait.compareTo(MAX_IDLE_WAIT) > 0 ? MAX_IDLE_WAIT : wait;
        } catch (RuntimeException e) {
            log.warn("Could not read the next due job: {}", e.getMessage());
            return Duration.ofMinutes(1);
        }
    }

    @Override
    public void stop() {
        running = false;
        wakeup.signal();
        long deadline = System.nanoTime() + SHUTDOWN_GRACE.toNanos();
        for (Thread t : threads) {
            joinUntil(t, deadline);
        }
        for (Thread t : threads) {
            if (t.isAlive()) {
                t.interrupt();
            }
        }
        long hardDeadline = System.nanoTime() + Duration.ofSeconds(4).toNanos();
        for (Thread t : threads) {
            joinUntil(t, hardDeadline);
        }
        threads.clear();
    }

    private static void joinUntil(Thread t, long deadlineNanos) {
        long remainingMillis = (deadlineNanos - System.nanoTime()) / 1_000_000;
        if (remainingMillis <= 0) {
            return;
        }
        try {
            t.join(remainingMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
