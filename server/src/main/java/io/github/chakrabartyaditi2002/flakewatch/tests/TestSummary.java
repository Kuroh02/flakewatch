package io.github.chakrabartyaditi2002.flakewatch.tests;

import java.time.Instant;

/** Matches {@code TestSummary} in api/openapi.yaml. {@code quarantined} is always false until M2. */
public record TestSummary(
        long id,
        String testKey,
        String suite,
        String name,
        String status,
        Instant statusSince,
        int impact,
        double flipRate,
        Instant lastFailureAt,
        boolean quarantined) {}
