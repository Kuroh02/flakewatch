package io.github.chakrabartyaditi2002.flakewatch.runs;

import java.time.Instant;
import java.util.Map;

/** Matches {@code RunSummary} in api/openapi.yaml. {@code totals} holds the {@code UploadSummary} fields. */
public record RunSummary(
        long id,
        String provider,
        String externalRunId,
        int attempt,
        String commitSha,
        String branch,
        Integer pullRequest,
        Instant startedAt,
        Map<String, Integer> totals,
        int failedTests) {}
