package io.github.chakrabartyaditi2002.flakewatch.failures;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.chakrabartyaditi2002.flakewatch.tests.TestSummary;
import java.time.Instant;
import java.util.List;

/**
 * Matches {@code FailureGroup} (with {@code tests} null) and {@code FailureGroupDetail} (with tests)
 * in api/openapi.yaml (FR-GRP-003).
 */
public record FailureGroupView(
        long id,
        String failureType,
        String normalizedMessage,
        String sampleMessage,
        List<String> topFrames,
        Instant firstSeenAt,
        Instant lastSeenAt,
        long occurrences,
        int affectedTests,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<TestSummary> tests) {}
