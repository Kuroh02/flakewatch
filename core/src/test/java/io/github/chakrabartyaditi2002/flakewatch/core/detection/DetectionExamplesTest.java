package io.github.chakrabartyaditi2002.flakewatch.core.detection;

import static io.github.chakrabartyaditi2002.flakewatch.core.report.ExecutionStatus.FAILED;
import static io.github.chakrabartyaditi2002.flakewatch.core.report.ExecutionStatus.PASSED;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.chakrabartyaditi2002.flakewatch.core.detection.Evidence.EvidenceKind;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The worked examples E01–E17 from docs/design/detection-rules.md §8.
 * History notation: final outcomes on the default branch, oldest first. [F→P] = failed then passed on retry.
 */
class DetectionExamplesTest {

    @Test
    @DisplayName("E01 (FR-DET-001): P P P P P is Stable")
    void e01() {
        Classification c = History.main().pass().pass().pass().pass().pass().classify();
        assertThat(c.overall()).isEqualTo(Status.STABLE);
        assertThat(c.evidence()).isEmpty();
    }

    @Test
    @DisplayName("E02 (FR-DET-001): P P is Not enough data")
    void e02() {
        assertThat(History.main().pass().pass().classify().overall()).isEqualTo(Status.NOT_ENOUGH_DATA);
    }

    @Test
    @DisplayName("E03 (FR-DET-002): P P [F→P] P P is Flaky with retry-pass evidence")
    void e03() {
        Classification c = History.main().pass().pass().retryPass().pass().pass().classify();
        assertThat(c.overall()).isEqualTo(Status.FLAKY);
        assertThat(c.evidence()).hasSize(1);
        Evidence e = c.evidence().get(0);
        assertThat(e.kind()).isEqualTo(EvidenceKind.RETRY_PASS);
        assertThat(e.runIds()).containsExactly(1003L);
        assertThat(e.explanation()).isEqualTo("Failed, then passed on retry 1 in run run-1003 (commit c000003)");
    }

    @Test
    @DisplayName("E04 (FR-DET-003): P P F P with the last F and P on the same commit is Flaky")
    void e04() {
        History h = History.main().pass().pass();
        h.upload("abc1234", FAILED).upload("abc1234", PASSED);
        Classification c = h.classify();
        assertThat(c.overall()).isEqualTo(Status.FLAKY);
        Evidence e = c.evidence().get(0);
        assertThat(e.kind()).isEqualTo(EvidenceKind.SAME_COMMIT_MIXED);
        assertThat(e.commitSha()).isEqualTo("abc1234");
        assertThat(e.runIds()).containsExactly(1003L, 1004L);
        assertThat(e.explanation()).isEqualTo("Passed once and failed once on the same commit abc1234");
    }

    @Test
    @DisplayName("E05 (FR-DET-005): P P P F F F is Broken")
    void e05() {
        assertThat(History.main().pass().pass().pass().fail().fail().fail().classify().overall())
                .isEqualTo(Status.BROKEN);
    }

    @Test
    @DisplayName("E06 (FR-DET-005): P P P P F is Failing")
    void e06() {
        assertThat(History.main().pass().pass().pass().pass().fail().classify().overall())
                .isEqualTo(Status.FAILING);
    }

    @Test
    @DisplayName("E07 (FR-DET-004): P F P P F P P has 2 recoveries and is Suspected")
    void e07() {
        assertThat(History.main().pass().fail().pass().pass().fail().pass().pass().classify().overall())
                .isEqualTo(Status.SUSPECTED);
    }

    @Test
    @DisplayName("E08 (FR-DET-004): P P F P P P has only 1 recovery and is Stable")
    void e08() {
        assertThat(History.main().pass().pass().fail().pass().pass().pass().classify().overall())
                .isEqualTo(Status.STABLE);
    }

    @Test
    @DisplayName("E09 (FR-DET-005): P [F→P] P F F F is Broken, because rule 1 comes before rule 2")
    void e09() {
        Classification c = History.main().pass().retryPass().pass().fail().fail().fail().classify();
        assertThat(c.overall()).isEqualTo(Status.BROKEN);
        assertThat(c.evidence()).hasSize(1);
    }

    @Test
    @DisplayName("E10 (FR-DET-002): P [F→P] P P F is Flaky, because rule 2 comes before rule 3")
    void e10() {
        assertThat(History.main().pass().retryPass().pass().pass().fail().classify().overall())
                .isEqualTo(Status.FLAKY);
    }

    @Test
    @DisplayName("E11 (FR-DET-010): a retry pass 20 days ago no longer counts; P P P P since is Stable")
    void e11() {
        History h = History.main().daysAgo(20).retryPass().daysAgo(5).pass().pass().pass().pass();
        Classification c = h.classify();
        assertThat(c.overall()).isEqualTo(Status.STABLE);
        assertThat(c.evidence()).isEmpty();
    }

    @Test
    @DisplayName("E12 (FR-DET-003): same commit passed then failed on a feature branch is Flaky")
    void e12() {
        History h = History.main().branch("feature/x");
        h.upload("c9c9c9c", PASSED).upload("c9c9c9c", FAILED);
        assertThat(h.classify().overall()).isEqualTo(Status.FLAKY);
    }

    @Test
    @DisplayName("E13 (FR-DET-001): P S S P is Not enough data, because skips are ignored")
    void e13() {
        assertThat(History.main().pass().skip().skip().pass().classify().overall())
                .isEqualTo(Status.NOT_ENOUGH_DATA);
    }

    @Test
    @DisplayName("E14 (FR-DET-002): P P [F→F→F] is Failing; retries that never pass are not evidence")
    void e14() {
        History h = History.main().pass().pass();
        h.upload(h.newCommit(), FAILED, FAILED, FAILED);
        Classification c = h.classify();
        assertThat(c.overall()).isEqualTo(Status.FAILING);
        assertThat(c.evidence()).isEmpty();
    }

    @Test
    @DisplayName("E15 (FR-DET-001): chrome Stable and firefox Flaky combine to Flaky overall")
    void e15() {
        History h = History.main().variant("chrome").pass().pass().pass().pass();
        h.variant("firefox").pass().retryPass().pass();
        Classification c = h.classify();
        assertThat(c.overall()).isEqualTo(Status.FLAKY);
        assertThat(c.byVariant()).containsEntry("chrome", Status.STABLE);
        assertThat(c.byVariant()).containsEntry("firefox", Status.FLAKY);
    }

    @Test
    @DisplayName("E16 (FR-DET-003): different variants never form evidence together")
    void e16() {
        History h = History.main().variant("chrome").pass().pass();
        h.upload("shared1", PASSED);
        h.variant("firefox").pass().pass();
        h.upload("shared1", FAILED);
        Classification c = h.classify();
        assertThat(c.overall()).isEqualTo(Status.FAILING);
        assertThat(c.byVariant()).containsEntry("chrome", Status.STABLE);
        assertThat(c.byVariant()).containsEntry("firefox", Status.FAILING);
        assertThat(c.evidence()).isEmpty();
    }

    @Test
    @DisplayName("E17 (FR-DET-001): no executions is Not enough data")
    void e17() {
        Classification c = Classifier.classify(List.of(), DetectionSettings.defaults(), History.NOW, "main");
        assertThat(c.overall()).isEqualTo(Status.NOT_ENOUGH_DATA);
        assertThat(c.byVariant()).isEmpty();
        assertThat(c.impact()).isEqualTo(0);
    }

    @Test
    @DisplayName("FR-DET-012: impact counts failed outcomes plus uploads that needed a retry; flip rate counts changes")
    void impactAndFlipRate() {
        Classification c = History.main().pass().fail().retryPass().pass().classify();
        // failed outcomes: 1 (the plain F); uploads needing a retry: 1 ([F→P])
        assertThat(c.impact()).isEqualTo(2);
        // D = P F P P → flips at positions 1 and 2 → 2 / 3
        assertThat(c.flipRate()).isEqualTo(2.0 / 3);
        assertThat(c.lastFailureAt()).isEqualTo(History.NOW.minus(java.time.Duration.ofDays(5)).plusSeconds(2 * 3600));
    }
}
