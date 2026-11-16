package io.github.chakrabartyaditi2002.flakewatch.runs;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Matches {@code RunDetail} in api/openapi.yaml: the {@link RunSummary} fields, flattened, plus the
 * run's uploads and its labelled failures (FR-UI-004).
 */
public record RunDetail(
        long id,
        String provider,
        String externalRunId,
        int attempt,
        String commitSha,
        String branch,
        Integer pullRequest,
        Instant startedAt,
        Map<String, Integer> totals,
        int failedTests,
        String repositoryUrl,
        List<UploadRef> uploads,
        List<RunFailure> failures) {

    static RunDetail of(RunSummary s, String repositoryUrl, List<UploadRef> uploads, List<RunFailure> failures) {
        return new RunDetail(s.id(), s.provider(), s.externalRunId(), s.attempt(), s.commitSha(), s.branch(),
                s.pullRequest(), s.startedAt(), s.totals(), s.failedTests(), repositoryUrl, uploads, failures);
    }

    public record UploadRef(long uploadId, String jobKey, String variant, String status) {}

    /** label is KNOWN_FLAKY, NEW_FAILURE, BROKEN, PASSED_ON_RETRY or QUARANTINED. */
    public record RunFailure(
            long testId, String testKey, String label, String status, Long failureGroupId, String message,
            String variant, String jobKey) {}
}
