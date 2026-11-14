package sample;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class SampleTest {

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
        throw new RuntimeException("Unexpected null pointer exception");
    }

    @Test
    @Disabled("Skipped for fixture demonstration")
    void testSkipped() {
        // Will not run
    }

    @Test
    void testFlaky() {
        if (flakyAttempts++ == 0) {
            fail("Flaky failure on first attempt");
        }
        // Passes on rerun
    }

    @ParameterizedTest
    @ValueSource(strings = {"alpha", "beta"})
    void testParameterized(String candidate) {
        if ("beta".equals(candidate)) {
            fail("Failure on parameter beta");
        }
    }
}
