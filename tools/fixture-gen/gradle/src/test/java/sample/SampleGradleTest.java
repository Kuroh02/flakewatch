package sample;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class SampleGradleTest {

    private static int flakyAttempts = 0;

    @Test
    void testPassing() {
        assertEquals(2, 1 + 1);
    }

    @Test
    void testFailing() {
        fail("Assertion failed: expected true but was false");
    }

    @Test
    void testErroring() {
        throw new RuntimeException("Unexpected runtime error in test");
    }

    @Test
    @Disabled("Skipped test for fixture")
    void testSkipped() {
        // Skipped
    }

    @Test
    void testFlaky() {
        if (flakyAttempts++ == 0) {
            fail("Flaky failure on attempt 1");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"alpha", "beta"})
    void testParameterized(String value) {
        if ("beta".equals(value)) {
            fail("Parameterized failure for beta");
        }
    }
}
