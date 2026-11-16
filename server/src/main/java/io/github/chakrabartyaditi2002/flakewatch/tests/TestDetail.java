package io.github.chakrabartyaditi2002.flakewatch.tests;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Matches {@code TestDetail} in api/openapi.yaml: the {@link TestSummary} fields, flattened, plus history (FR-UI-003). */
public record TestDetail(
        long id,
        String testKey,
        String suite,
        String name,
        String status,
        Instant statusSince,
        int impact,
        double flipRate,
        Instant lastFailureAt,
        boolean quarantined,
        String methodKey,
        String file,
        List<VariantStatus> variants,
        List<Map<String, Object>> evidence,
        List<TimelinePoint> timeline,
        List<FailureGroupRef> failureGroups,
        List<DurationPoint> durationTrend,
        @JsonInclude(JsonInclude.Include.NON_NULL) Object quarantine,
        boolean readyToRelease) {

    public record VariantStatus(String variant, String status) {}

    /** outcome is PASSED, FAILED, SKIPPED or PASSED_ON_RETRY. */
    public record TimelinePoint(
            long runId, Instant executedAt, String outcome, String commitSha, String branch, String variant) {}

    public record FailureGroupRef(long id, String failureType, String normalizedMessage, long occurrences) {}

    public record DurationPoint(LocalDate day, long averageMs) {}
}
