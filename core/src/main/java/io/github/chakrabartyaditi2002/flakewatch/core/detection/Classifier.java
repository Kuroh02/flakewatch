package io.github.chakrabartyaditi2002.flakewatch.core.detection;

import io.github.chakrabartyaditi2002.flakewatch.core.detection.Evidence.EvidenceKind;
import io.github.chakrabartyaditi2002.flakewatch.core.report.ExecutionStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Decides a test's status from its executions. A pure function: no clock, no database, no randomness.
 *
 * <p>This is a direct translation of docs/design/detection-rules.md. Section numbers in comments
 * point there. FR-DET-001 to FR-DET-007, FR-DET-012.
 */
public final class Classifier {

    public static final int MAX_EVIDENCE = 5;

    private Classifier() {}

    public static Classification classify(
            Collection<Execution> executions, DetectionSettings settings, Instant now, String defaultBranch) {
        Instant windowStart = now.minus(Duration.ofDays(settings.windowDays()));

        // §3 steps 1 and 2: drop old data, then duplicates (keeping a choice that doesn't depend on input order)
        Map<String, Execution> unique = new HashMap<>();
        for (Execution e : executions) {
            if (!e.executedAt().isBefore(windowStart)) {
                unique.merge(e.identity(), e, (a, b) -> Execution.TOTAL.compare(a, b) <= 0 ? a : b);
            }
        }

        // §3 steps 3 and 4: sort by data only, then split by variant
        List<Execution> sorted = new ArrayList<>(unique.values());
        sorted.sort(Execution.ORDER);
        Map<String, List<Execution>> byVariant = new TreeMap<>();
        for (Execution e : sorted) {
            byVariant.computeIfAbsent(e.variant(), v -> new ArrayList<>()).add(e);
        }

        Map<String, Status> statuses = new TreeMap<>();
        List<Evidence> evidence = new ArrayList<>();
        int impact = 0;
        double flipRate = 0;
        Instant lastFailureAt = null;
        for (Map.Entry<String, List<Execution>> entry : byVariant.entrySet()) {
            VariantResult r = classifyVariant(entry.getKey(), entry.getValue(), settings, defaultBranch);
            statuses.put(entry.getKey(), r.status());
            evidence.addAll(r.evidence());
            impact += r.impact();
            flipRate = Math.max(flipRate, r.flipRate());
            if (r.lastFailureAt() != null && (lastFailureAt == null || r.lastFailureAt().isAfter(lastFailureAt))) {
                lastFailureAt = r.lastFailureAt();
            }
        }

        // §6: the overall status is the first variant status in attention order
        Status overall = statuses.values().stream()
                .min(Comparator.comparingInt(Status::attentionRank))
                .orElse(Status.NOT_ENOUGH_DATA);

        evidence.sort(Comparator.comparing(Evidence::observedAt)
                .reversed()
                .thenComparing(Evidence::kind)
                .thenComparing(Evidence::variant)
                .thenComparing(Evidence::commitSha)
                .thenComparing(ev -> ev.runIds().toString()));
        List<Evidence> top = evidence.subList(0, Math.min(MAX_EVIDENCE, evidence.size()));

        return new Classification(overall, statuses, top, impact, flipRate, lastFailureAt);
    }

    /** The final outcome of a test in one upload (§3 step 5). */
    private record Outcome(Execution first, boolean failed, Integer retryPassIndex, boolean neededRetry) {}

    private record VariantResult(
            Status status, List<Evidence> evidence, int impact, double flipRate, Instant lastFailureAt) {}

    private static VariantResult classifyVariant(
            String variant, List<Execution> executions, DetectionSettings settings, String defaultBranch) {

        Map<String, List<Execution>> byUpload = new LinkedHashMap<>();
        Instant lastFailureAt = null;
        for (Execution e : executions) {
            byUpload.computeIfAbsent(e.uploadKey(), k -> new ArrayList<>()).add(e);
            if (e.status().isFailure() && (lastFailureAt == null || e.executedAt().isAfter(lastFailureAt))) {
                lastFailureAt = e.executedAt();
            }
        }

        // §3 step 5: one final outcome per upload; skipped executions are dropped
        List<Outcome> all = new ArrayList<>();
        for (List<Execution> uploadExecutions : byUpload.values()) {
            List<Execution> attempts = new ArrayList<>(uploadExecutions);
            attempts.removeIf(e -> e.status() == ExecutionStatus.SKIPPED);
            if (attempts.isEmpty()) {
                continue;
            }
            attempts.sort(Comparator.comparingInt(Execution::executionIndex));
            Integer retryPassIndex = null;
            boolean seenFailure = false;
            for (Execution e : attempts) {
                if (e.status().isFailure()) {
                    seenFailure = true;
                } else if (seenFailure && e.status() == ExecutionStatus.PASSED) {
                    retryPassIndex = e.executionIndex();
                    break;
                }
            }
            boolean neededRetry = false;
            for (int i = 0; i < attempts.size() - 1; i++) {
                if (attempts.get(i).status().isFailure()) {
                    neededRetry = true;
                    break;
                }
            }
            boolean failed = attempts.get(attempts.size() - 1).status().isFailure();
            all.add(new Outcome(attempts.get(0), failed, retryPassIndex, neededRetry));
        }
        List<Outcome> defaultBranchOutcomes = all.stream()
                .filter(o -> o.first().branch().equals(defaultBranch))
                .toList();

        List<Evidence> evidence = findEvidence(variant, all);
        Status status = applyRules(all, defaultBranchOutcomes, evidence, settings);

        int impact = 0;
        for (Outcome o : all) {
            impact += (o.failed() ? 1 : 0) + (o.neededRetry() ? 1 : 0);
        }
        return new VariantResult(status, evidence, impact, flipRate(defaultBranchOutcomes), lastFailureAt);
    }

    /** §5: the first rule that matches decides. */
    private static Status applyRules(
            List<Outcome> all, List<Outcome> d, List<Evidence> evidence, DetectionSettings settings) {
        int streak = settings.brokenStreak();
        if (d.size() >= streak && d.subList(d.size() - streak, d.size()).stream().allMatch(Outcome::failed)) {
            return Status.BROKEN;
        }
        if (!evidence.isEmpty()) {
            return Status.FLAKY;
        }
        if (!d.isEmpty() && d.get(d.size() - 1).failed()) {
            return Status.FAILING;
        }
        if (recoveries(d) >= settings.suspectedRecoveries()) {
            return Status.SUSPECTED;
        }
        if (all.size() < DetectionSettings.MIN_RESULTS_FOR_VERDICT) {
            return Status.NOT_ENOUGH_DATA;
        }
        return Status.STABLE;
    }

    /** A recovery is a failure immediately followed by a pass. */
    private static int recoveries(List<Outcome> d) {
        int count = 0;
        for (int i = 1; i < d.size(); i++) {
            if (d.get(i - 1).failed() && !d.get(i).failed()) {
                count++;
            }
        }
        return count;
    }

    /** §9: flips / (len(D) - 1), or 0 for fewer than 2 results. */
    private static double flipRate(List<Outcome> d) {
        if (d.size() < 2) {
            return 0;
        }
        int flips = 0;
        for (int i = 1; i < d.size(); i++) {
            if (d.get(i).failed() != d.get(i - 1).failed()) {
                flips++;
            }
        }
        return (double) flips / (d.size() - 1);
    }

    /** §4: retry passes, and passes and failures on the same commit (on any branch). */
    private static List<Evidence> findEvidence(String variant, List<Outcome> all) {
        List<Evidence> evidence = new ArrayList<>();
        for (Outcome o : all) {
            if (o.retryPassIndex() != null) {
                Execution e = o.first();
                evidence.add(new Evidence(
                        EvidenceKind.RETRY_PASS,
                        List.of(e.runId()),
                        e.commitSha(),
                        variant,
                        e.executedAt(),
                        "Failed, then passed on retry " + o.retryPassIndex() + " in run " + e.externalRunId()
                                + " (commit " + shortSha(e.commitSha()) + ")"));
            }
        }

        Map<String, List<Outcome>> byCommit = new TreeMap<>();
        for (Outcome o : all) {
            byCommit.computeIfAbsent(o.first().commitSha(), c -> new ArrayList<>()).add(o);
        }
        for (Map.Entry<String, List<Outcome>> entry : byCommit.entrySet()) {
            List<Outcome> outcomes = entry.getValue();
            long passed = outcomes.stream().filter(o -> !o.failed()).count();
            long failed = outcomes.size() - passed;
            if (passed > 0 && failed > 0) {
                TreeSet<Long> runIds = new TreeSet<>();
                Instant observedAt = Instant.MIN;
                for (Outcome o : outcomes) {
                    runIds.add(o.first().runId());
                    if (o.first().executedAt().isAfter(observedAt)) {
                        observedAt = o.first().executedAt();
                    }
                }
                evidence.add(new Evidence(
                        EvidenceKind.SAME_COMMIT_MIXED,
                        new ArrayList<>(runIds),
                        entry.getKey(),
                        variant,
                        observedAt,
                        "Passed " + times(passed) + " and failed " + times(failed) + " on the same commit "
                                + shortSha(entry.getKey())));
            }
        }
        return evidence;
    }

    private static String times(long n) {
        return n == 1 ? "once" : n == 2 ? "twice" : n + " times";
    }

    private static String shortSha(String sha) {
        return sha.length() > 7 ? sha.substring(0, 7) : sha;
    }
}
