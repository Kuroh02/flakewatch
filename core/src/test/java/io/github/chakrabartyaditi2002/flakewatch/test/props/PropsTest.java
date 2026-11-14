package io.github.chakrabartyaditi2002.flakewatch.test.props;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PropsTest {

    @Test
    @DisplayName("NFR-QUAL-001: same seed produces identical sequence of generated values")
    void sameSeedProducesIdenticalSequence() {
        Generator<Integer> intGen = Gens.integers(0, 1000);

        List<Integer> run1 = new ArrayList<>();
        Props.forAll(intGen, 100, 12345L, run1::add);

        List<Integer> run2 = new ArrayList<>();
        Props.forAll(intGen, 100, 12345L, run2::add);

        assertThat(run1).isEqualTo(run2);
    }

    @Test
    @DisplayName("NFR-QUAL-001: property passing on all inputs completes normally")
    void passingPropertyCompletes() {
        Generator<Integer> evenGen = Gens.integers(0, 100).map(i -> i * 2);
        Props.forAll(evenGen, 200, 42L, i -> {
            assertThat(i % 2).isZero();
        });
    }

    @Test
    @DisplayName("NFR-QUAL-001: failing property prints seed and case number for exact replay")
    void failingPropertyPrintsSeed() {
        Generator<Integer> intGen = Gens.integers(1, 100);

        assertThatThrownBy(() -> Props.forAll(intGen, 50, 9999L, i -> {
            if (i > 50) {
                throw new AssertionError("Value " + i + " exceeded 50");
            }
        }))
        .isInstanceOf(PropertyAssertionError.class)
        .hasMessageContaining("props.seed=9999")
        .hasMessageContaining("Failed at case");
    }

    @Test
    @DisplayName("NFR-QUAL-001: list shrinking isolates the minimal failing element")
    void listShrinkingIsolatesMinimalElement() {
        Generator<List<Integer>> listGen = Gens.lists(Gens.integers(1, 100), 5, 20);

        assertThatThrownBy(() -> Props.forAll(listGen, 50, 7777L, list -> {
            if (list.contains(42)) {
                throw new AssertionError("List contains forbidden 42");
            }
        }))
        .isInstanceOf(PropertyAssertionError.class)
        .satisfies(err -> {
            PropertyAssertionError pe = (PropertyAssertionError) err;
            assertThat(pe.getShrunkInput()).isEqualTo(List.of(42));
        });
    }
}
