package io.github.chakrabartyaditi2002.flakewatch.core.detection;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.chakrabartyaditi2002.flakewatch.core.report.ExecutionStatus;
import io.github.chakrabartyaditi2002.flakewatch.test.props.Generator;
import io.github.chakrabartyaditi2002.flakewatch.test.props.Gens;
import io.github.chakrabartyaditi2002.flakewatch.test.props.Props;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** PROP-DET-01 to PROP-DET-11 from docs/design/detection-rules.md §7. FR-DET-006. */
class ClassifierProperties {

    private static final Instant NOW = History.NOW;
    private static final DetectionSettings SETTINGS = DetectionSettings.defaults();
    private static final String[] VARIANTS = {"", "firefox"};
    private static final String[] BRANCHES = {"main", "main", "feature/x"};
    private static final String[] COMMITS = {"c000001", "c000002", "c000003", "c000004"};

    /** A seeded random history: uploads of 1–3 executions on a few variants, branches and shared commits. */
    static final Generator<List<Execution>> HISTORIES = rnd -> {
        List<Execution> out = new ArrayList<>();
        int uploads = rnd.nextInt(0, 14);
        for (int u = 0; u < uploads; u++) {
            out.addAll(upload(rnd, "run-" + u, randomTime(rnd, 0, SETTINGS.windowDays() - 1), randomStatuses(rnd)));
        }
        return out;
    };

    static List<Execution> upload(SplittableRandom rnd, String runId, Instant at, List<ExecutionStatus> statuses) {
        String variant = VARIANTS[rnd.nextInt(VARIANTS.length)];
        String branch = BRANCHES[rnd.nextInt(BRANCHES.length)];
        String commit = COMMITS[rnd.nextInt(COMMITS.length)];
        long dbRunId = Math.abs(runId.hashCode());
        List<Execution> out = new ArrayList<>();
        for (int i = 0; i < statuses.size(); i++) {
            out.add(new Execution("GITHUB", runId, 1, "default", variant, branch, commit, i, statuses.get(i), at, dbRunId));
        }
        return out;
    }

    static List<ExecutionStatus> randomStatuses(SplittableRandom rnd) {
        ExecutionStatus[] pool = {
            ExecutionStatus.PASSED, ExecutionStatus.PASSED, ExecutionStatus.PASSED,
            ExecutionStatus.FAILED, ExecutionStatus.ERRORED, ExecutionStatus.SKIPPED
        };
        List<ExecutionStatus> statuses = new ArrayList<>();
        int n = rnd.nextInt(1, 4);
        for (int i = 0; i < n; i++) {
            statuses.add(pool[rnd.nextInt(pool.length)]);
        }
        return statuses;
    }

    static Instant randomTime(SplittableRandom rnd, int minDaysAgo, int maxDaysAgo) {
        long minutes = rnd.nextLong(minDaysAgo * 1440L, maxDaysAgo * 1440L + 1);
        return NOW.minus(Duration.ofMinutes(minutes));
    }

    private static Classification classify(List<Execution> executions) {
        return Classifier.classify(executions, SETTINGS, NOW, "main");
    }

    @Test
    @DisplayName("PROP-DET-01: every input gives exactly one status per variant and one overall status")
    void total() {
        Props.forAll(HISTORIES, h -> {
            Classification c = classify(h);
            assertThat(c.overall()).isNotNull();
            assertThat(c.byVariant().keySet())
                    .isEqualTo(h.stream().map(Execution::variant).collect(java.util.stream.Collectors.toSet()));
            if (h.isEmpty()) {
                assertThat(c.overall()).isEqualTo(Status.NOT_ENOUGH_DATA);
            }
        });
    }

    @Test
    @DisplayName("PROP-DET-02: shuffling the input never changes the result")
    void orderDoesNotMatter() {
        Generator<long[]> seeds = rnd -> new long[] {rnd.nextLong(), rnd.nextLong()};
        Props.forAll(seeds, s -> {
            List<Execution> h = HISTORIES.generate(new SplittableRandom(s[0]));
            assertThat(classify(Gens.shuffle(h, new SplittableRandom(s[1])))).isEqualTo(classify(h));
        });
    }

    @Test
    @DisplayName("PROP-DET-03: adding copies of existing executions never changes the result")
    void duplicatesDoNotMatter() {
        Generator<long[]> seeds = rnd -> new long[] {rnd.nextLong(), rnd.nextLong()};
        Props.forAll(seeds, s -> {
            List<Execution> h = HISTORIES.generate(new SplittableRandom(s[0]));
            SplittableRandom rnd = new SplittableRandom(s[1]);
            List<Execution> withCopies = new ArrayList<>(h);
            for (Execution e : h) {
                if (rnd.nextBoolean()) {
                    withCopies.add(e);
                }
            }
            assertThat(classify(withCopies)).isEqualTo(classify(h));
        });
    }

    @Test
    @DisplayName("PROP-DET-04: no evidence means no Flaky status anywhere (Constitution P1)")
    void noProofNoFlaky() {
        Props.forAll(HISTORIES, h -> {
            Classification c = classify(h);
            if (c.evidence().isEmpty()) {
                assertThat(c.overall()).isNotEqualTo(Status.FLAKY);
                assertThat(c.byVariant().containsValue(Status.FLAKY)).isFalse();
            }
        });
    }

    @Test
    @DisplayName("PROP-DET-05: if every result passed, the status is Stable or Not enough data")
    void allPassesAreClean() {
        Props.forAll(HISTORIES, h -> {
            boolean allPassed = h.stream().allMatch(e -> e.status() == ExecutionStatus.PASSED || e.status() == ExecutionStatus.SKIPPED);
            if (allPassed) {
                assertThat(classify(h).overall()).isIn(Status.STABLE, Status.NOT_ENOUGH_DATA);
            }
        });
    }

    @Test
    @DisplayName("PROP-DET-06: if evidence exists, the status is Flaky or Broken")
    void proofShows() {
        Props.forAll(HISTORIES, h -> {
            Classification c = classify(h);
            if (!c.evidence().isEmpty()) {
                assertThat(c.overall()).isIn(Status.FLAKY, Status.BROKEN);
            }
        });
    }

    @Test
    @DisplayName("PROP-DET-07: adding executions older than the window never changes the result")
    void oldDataIsIgnored() {
        Generator<long[]> seeds = rnd -> new long[] {rnd.nextLong(), rnd.nextLong()};
        Props.forAll(seeds, s -> {
            List<Execution> h = HISTORIES.generate(new SplittableRandom(s[0]));
            SplittableRandom rnd = new SplittableRandom(s[1]);
            List<Execution> withOld = new ArrayList<>(h);
            int extra = rnd.nextInt(1, 5);
            for (int i = 0; i < extra; i++) {
                withOld.addAll(upload(rnd, "old-" + i, randomTime(rnd, SETTINGS.windowDays() + 1, 90), randomStatuses(rnd)));
            }
            assertThat(classify(withOld)).isEqualTo(classify(h));
        });
    }

    @Test
    @DisplayName("PROP-DET-08: for a variant with no failures, extra passes can only turn Not enough data into Stable")
    void passesCannotHurtACleanTest() {
        Generator<long[]> seeds = rnd -> new long[] {rnd.nextLong(), rnd.nextLong()};
        Props.forAll(seeds, s -> {
            List<Execution> h = HISTORIES.generate(new SplittableRandom(s[0]));
            SplittableRandom rnd = new SplittableRandom(s[1]);
            Classification before = classify(h);
            List<Execution> more = new ArrayList<>(h);
            int extra = rnd.nextInt(1, 5);
            for (int i = 0; i < extra; i++) {
                more.addAll(upload(rnd, "pass-" + i, randomTime(rnd, 0, 13), List.of(ExecutionStatus.PASSED)));
            }
            Classification after = classify(more);
            for (Map.Entry<String, Status> entry : before.byVariant().entrySet()) {
                boolean hadFailures = h.stream()
                        .anyMatch(e -> e.variant().equals(entry.getKey()) && e.status().isFailure());
                if (!hadFailures) {
                    Status was = entry.getValue();
                    Status now = after.byVariant().get(entry.getKey());
                    if (was == Status.NOT_ENOUGH_DATA) {
                        assertThat(now).isIn(Status.NOT_ENOUGH_DATA, Status.STABLE);
                    } else {
                        assertThat(now).isEqualTo(was);
                    }
                }
            }
        });
    }

    @Test
    @DisplayName("PROP-DET-09: the overall status is the first variant status in attention order")
    void variantCombination() {
        Props.forAll(HISTORIES, h -> {
            Classification c = classify(h);
            Status expected = c.byVariant().values().stream()
                    .min(Comparator.comparingInt(Status::attentionRank))
                    .orElse(Status.NOT_ENOUGH_DATA);
            assertThat(c.overall()).isEqualTo(expected);
        });
    }

    @Test
    @DisplayName("PROP-DET-10: flip rate is between 0 and 1; constant history gives 0, alternating gives 1")
    void flipRateIsSane() {
        Props.forAll(HISTORIES, h -> assertThat(classify(h).flipRate()).isBetween(0.0, 1.0));
        Props.forAll(Gens.integers(2, 12), n -> {
            History constant = History.main();
            History alternating = History.main();
            for (int i = 0; i < n; i++) {
                constant.pass();
                if (i % 2 == 0) {
                    alternating.pass();
                } else {
                    alternating.fail();
                }
            }
            assertThat(constant.classify().flipRate()).isEqualTo(0.0);
            assertThat(alternating.classify().flipRate()).isEqualTo(1.0);
        });
    }

    @Test
    @DisplayName("PROP-DET-11: impact is never negative and adding a failed result never lowers it")
    void impactGrowsWithFailures() {
        Generator<long[]> seeds = rnd -> new long[] {rnd.nextLong(), rnd.nextLong()};
        Props.forAll(seeds, s -> {
            List<Execution> h = HISTORIES.generate(new SplittableRandom(s[0]));
            SplittableRandom rnd = new SplittableRandom(s[1]);
            Classification before = classify(h);
            assertThat(before.impact()).isGreaterThanOrEqualTo(0);
            List<Execution> more = new ArrayList<>(h);
            more.addAll(upload(rnd, "fail-extra", randomTime(rnd, 0, 13), List.of(ExecutionStatus.FAILED)));
            assertThat(classify(more).impact()).isGreaterThanOrEqualTo(before.impact());
        });
    }
}
