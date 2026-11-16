package io.github.chakrabartyaditi2002.flakewatch.projects;

import io.github.chakrabartyaditi2002.flakewatch.runs.RunSummary;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Matches {@code ProjectSummary} in api/openapi.yaml (FR-UI-001). */
public record ProjectSummary(
        int totalTests, Map<String, Integer> countsByStatus, List<TrendPoint> flakyTrend, List<RunSummary> recentRuns) {

    public record TrendPoint(LocalDate day, int flaky, int suspected, int broken) {}
}
