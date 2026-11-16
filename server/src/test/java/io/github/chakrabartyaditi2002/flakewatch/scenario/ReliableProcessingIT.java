package io.github.chakrabartyaditi2002.flakewatch.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.chakrabartyaditi2002.flakewatch.core.report.Fixtures;
import io.github.chakrabartyaditi2002.flakewatch.jobs.Job;
import io.github.chakrabartyaditi2002.flakewatch.jobs.JobHandler;
import io.github.chakrabartyaditi2002.flakewatch.jobs.JobKinds;
import io.github.chakrabartyaditi2002.flakewatch.jobs.JobQueue;
import io.github.chakrabartyaditi2002.flakewatch.test.AbstractApiIT;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Constitution Article 3 on a host that can sleep or restart mid-job:
 * accepted uploads are never lost, and nothing is ever counted twice (NFR-REL-001, NFR-REL-002).
 */
@DisplayName("Reliable background processing")
@Import(ReliableProcessingIT.TestHandlers.class)
class ReliableProcessingIT extends AbstractApiIT {

    static final Map<Long, AtomicInteger> RUNS_PER_JOB = new ConcurrentHashMap<>();

    @TestConfiguration
    static class TestHandlers {
        @Bean
        JobHandler countingHandler() {
            return new JobHandler() {
                @Override
                public String kind() {
                    return "TEST_COUNT";
                }

                @Override
                public void handle(Job job) {
                    RUNS_PER_JOB.computeIfAbsent(job.id(), id -> new AtomicInteger()).incrementAndGet();
                }
            };
        }

        @Bean
        JobHandler brokenHandler() {
            return new JobHandler() {
                @Override
                public String kind() {
                    return "TEST_ALWAYS_FAILS";
                }

                @Override
                public void handle(Job job) {
                    throw new IllegalStateException("downstream service unavailable");
                }
            };
        }
    }

    @Autowired
    private JobQueue queue;

    @BeforeEach
    void resetCounters() {
        RUNS_PER_JOB.clear();
    }

    private long count(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }

    @Test
    @DisplayName("NFR-REL-002: processing the same upload again (e.g. after a crash just before commit) changes nothing")
    void reprocessingIsHarmless() {
        String token = createProject("shop", "PUBLIC");
        long uploadId = ciJob(token).report("TEST-sample.SampleTest.xml", Fixtures.bytes("maven-surefire/TEST-sample.SampleTest.xml"))
                .upload().jsonPath().getLong("uploadId");
        processQueuedJobs();
        long executions = count("select count(*) from test_executions");
        long passed = count("select coalesce(sum(passed), 0) from test_daily_stats");
        long occurrences = count("select coalesce(sum(occurrences), 0) from failure_groups");

        queue.enqueue(JobKinds.PROCESS_UPLOAD, Map.of("uploadId", uploadId), null, clock.instant());
        processQueuedJobs();

        assertThat(count("select count(*) from test_executions")).isEqualTo(executions);
        assertThat(count("select coalesce(sum(passed), 0) from test_daily_stats")).isEqualTo(passed);
        assertThat(count("select coalesce(sum(occurrences), 0) from failure_groups")).isEqualTo(occurrences);
        assertThat(occurrences).isGreaterThan(0L);
    }

    @Test
    @DisplayName("NFR-REL-001: if a worker stalls past its lease, another finishes the job and the stalled one can't commit")
    void leaseTakeover() {
        queue.enqueue("TEST_COUNT", Map.of(), null, clock.instant());
        Optional<Job> stalled = queue.claim(clock.instant(), Duration.ofMinutes(5), new UUID(0, 1));
        assertThat(stalled.isPresent()).isTrue();

        // The free host froze the first worker; six minutes later its lease has expired
        clock.advance(Duration.ofMinutes(6));
        assertThat(worker.runOnce()).isTrue();

        assertThat(queue.complete(stalled.get(), clock.instant())).isFalse();
        assertThat(RUNS_PER_JOB.get(stalled.get().id()).get()).isEqualTo(1);
        assertThat(count("select count(*) from jobs where status = 'DONE'")).isEqualTo(1L);
    }

    @Test
    @DisplayName("NFR-REL-001: a job that keeps failing is retried with backoff, then marked DEAD after 5 attempts")
    void poisonPillStopsAfterFiveAttempts() {
        queue.enqueue("TEST_ALWAYS_FAILS", Map.of(), null, clock.instant());
        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThat(worker.runOnce()).isTrue();
            // Not due again until the backoff has passed
            assertThat(worker.runOnce()).isFalse();
            clock.advance(Duration.ofHours(2));
        }
        assertThat(worker.runOnce()).isFalse();
        assertThat(jdbc.sql("select status from jobs").query(String.class).single()).isEqualTo("DEAD");
        assertThat(jdbc.sql("select last_error from jobs").query(String.class).single())
                .contains("downstream service unavailable");
    }

    @Test
    @DisplayName("NFR-REL-001/002: four workers sharing 200 jobs run each one exactly once")
    void concurrentWorkersRunEachJobOnce() throws Exception {
        for (int i = 0; i < 200; i++) {
            queue.enqueue("TEST_COUNT", Map.of("n", i), null, clock.instant());
        }
        List<Thread> threads = new ArrayList<>();
        List<Throwable> errors = new ArrayList<>();
        for (int t = 0; t < 4; t++) {
            Thread thread = new Thread(() -> {
                try {
                    worker.runUntilIdle();
                } catch (Throwable e) {
                    synchronized (errors) {
                        errors.add(e);
                    }
                }
            });
            threads.add(thread);
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join(60_000);
        }

        assertThat(errors).isEmpty();
        assertThat(RUNS_PER_JOB).hasSize(200);
        assertThat(RUNS_PER_JOB.values().stream().allMatch(c -> c.get() == 1)).isTrue();
        assertThat(count("select count(*) from jobs where status = 'DONE'")).isEqualTo(200L);
    }
}
