package io.github.chakrabartyaditi2002.flakewatch.core.testkey;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.chakrabartyaditi2002.flakewatch.test.props.Generator;
import io.github.chakrabartyaditi2002.flakewatch.test.props.Gens;
import io.github.chakrabartyaditi2002.flakewatch.test.props.Props;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TestKeysTest {

    @Test
    @DisplayName("FR-ING-010: test key is <suite>#<name> with whitespace tidied; parameters are kept")
    void testKey() {
        assertThat(TestKeys.testKey("com.shop.CartTest", "addsItem")).isEqualTo("com.shop.CartTest#addsItem");
        assertThat(TestKeys.testKey("  a.B ", " adds(int)[2] ")).isEqualTo("a.B#adds(int)[2]");
        assertThat(TestKeys.testKey("Suite", "two\n  words\there")).isEqualTo("Suite#two words here");
    }

    @Test
    @DisplayName("FR-ING-010: method key drops the parameter suffix")
    void methodKey() {
        assertThat(TestKeys.methodKey("a.CartTest", "adds(int)[2]")).isEqualTo("a.CartTest#adds");
        assertThat(TestKeys.methodKey("a.T", "testParameterized[beta, false](2)")).isEqualTo("a.T#testParameterized");
        assertThat(TestKeys.methodKey("a.T", "plain")).isEqualTo("a.T#plain");
        // A name made only of parameters (Gradle's display names) is kept whole rather than becoming empty
        assertThat(TestKeys.methodKey("a.T", "[1] \"alpha\"")).isEqualTo("a.T#[1] \"alpha\"");
    }

    @Test
    @DisplayName("FR-ING-010: very long keys are capped at 2,000 characters with a hash suffix, and stay unique")
    void longKeys() {
        String a = TestKeys.testKey("S", "x".repeat(3000) + "a");
        String b = TestKeys.testKey("S", "x".repeat(3000) + "b");
        assertThat(a).hasSize(TestKeys.MAX_KEY_LENGTH);
        assertThat(b).hasSize(TestKeys.MAX_KEY_LENGTH);
        assertThat(a).isNotEqualTo(b);
        assertThat(a).matches(".*~[0-9a-f]{8}");
    }

    private static final Generator<String> NAMES = Gens.strings(0, 40, "abcXYZ019 ()[]#.,\t\n\"");

    @Test
    @DisplayName("FR-ING-010: tidying is idempotent")
    void tidyIdempotent() {
        Props.forAll(NAMES, s -> assertThat(TestKeys.tidy(TestKeys.tidy(s))).isEqualTo(TestKeys.tidy(s)));
    }

    @Test
    @DisplayName("FR-ING-010: keys are deterministic and the method key is a prefix of the test key")
    void methodKeyIsPrefix() {
        Generator<String[]> pairs = rnd -> new String[] {NAMES.generate(rnd), NAMES.generate(rnd)};
        Props.forAll(pairs, p -> {
            String testKey = TestKeys.testKey(p[0], p[1]);
            assertThat(TestKeys.testKey(p[0], p[1])).isEqualTo(testKey);
            assertThat(Arrays.equals(TestKeys.keyHash(testKey), TestKeys.keyHash(testKey))).isTrue();
            assertThat(testKey).startsWith(TestKeys.methodKey(p[0], p[1]));
        });
    }
}
