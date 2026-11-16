package io.github.chakrabartyaditi2002.flakewatch.jobs;

import java.util.Map;
import java.util.UUID;

/**
 * A claimed job.
 *
 * @param leaseToken the fencing token: only the worker holding it can complete the job
 */
public record Job(long id, String kind, Map<String, Object> payload, int attempts, int maxAttempts, UUID leaseToken) {

    public Job {
        payload = Map.copyOf(payload);
    }
}
