package io.github.chakrabartyaditi2002.flakewatch.tests;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/** Matches {@code Execution} in api/openapi.yaml. */
public record ExecutionView(
        long id,
        long runId,
        long uploadId,
        int executionIndex,
        @JsonProperty("isFinal") boolean isFinal,
        String status,
        boolean quarantined,
        int durationMs,
        Instant executedAt,
        String commitSha,
        String branch,
        String variant,
        Long failureGroupId,
        String failureType,
        String failureMessage,
        String stackTrace) {}
