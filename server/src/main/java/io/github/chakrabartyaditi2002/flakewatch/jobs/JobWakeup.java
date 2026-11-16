package io.github.chakrabartyaditi2002.flakewatch.jobs;

import java.time.Duration;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Component;

/**
 * Lets an idle worker sleep until there is work, instead of polling the database (PLAN §8.4).
 * The upload API calls {@link #signal()} after its transaction commits.
 */
@Component
public class JobWakeup {

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition work = lock.newCondition();
    private long signals;

    public void signal() {
        lock.lock();
        try {
            signals++;
            work.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /** The current signal count; pass it to {@link #await} so a signal sent in between isn't missed. */
    public long generation() {
        lock.lock();
        try {
            return signals;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Waits until a signal newer than {@code seenGeneration} arrives, or the timeout passes.
     * A null timeout waits until a signal.
     */
    public void await(long seenGeneration, Duration timeout) throws InterruptedException {
        lock.lock();
        try {
            long nanos = timeout == null ? Long.MAX_VALUE : Math.max(0, timeout.toNanos());
            while (signals == seenGeneration && nanos > 0) {
                if (timeout == null) {
                    work.await();
                } else {
                    nanos = work.awaitNanos(nanos);
                }
            }
        } finally {
            lock.unlock();
        }
    }
}
