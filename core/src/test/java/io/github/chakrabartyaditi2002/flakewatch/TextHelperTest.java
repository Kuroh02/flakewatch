package io.github.chakrabartyaditi2002.flakewatch;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TextHelperTest {

    @Test
    @DisplayName("NFR-QUAL-001: isBlank returns true for null, empty or whitespace")
    void testIsBlank() {
        assertThat(TextHelper.isBlank(null)).isTrue();
        assertThat(TextHelper.isBlank("")).isTrue();
        assertThat(TextHelper.isBlank("   ")).isTrue();
        assertThat(TextHelper.isBlank("abc")).isFalse();
    }
}
