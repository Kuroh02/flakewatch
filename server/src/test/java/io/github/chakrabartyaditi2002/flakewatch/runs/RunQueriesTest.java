package io.github.chakrabartyaditi2002.flakewatch.runs;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RunQueriesTest {

    @Test
    @DisplayName("US-02 #1: each failed test in a run is labelled known flaky, new failure or broken")
    void labelsFailures() {
        assertThat(RunQueries.label("FAILED", false, false, "FLAKY")).isEqualTo("KNOWN_FLAKY");
        assertThat(RunQueries.label("FAILED", false, false, "STABLE")).isEqualTo("NEW_FAILURE");
        assertThat(RunQueries.label("ERRORED", false, false, "FAILING")).isEqualTo("NEW_FAILURE");
        assertThat(RunQueries.label("FAILED", false, false, "BROKEN")).isEqualTo("BROKEN");
    }

    @Test
    @DisplayName("US-02 #2: a test that failed and then passed on retry is 'passed on retry'")
    void passedOnRetry() {
        assertThat(RunQueries.label("PASSED", false, true, "FLAKY")).isEqualTo("PASSED_ON_RETRY");
    }

    @Test
    @DisplayName("FR-ING-013: a failure muted by quarantine is labelled as quarantined")
    void quarantined() {
        assertThat(RunQueries.label("FAILED", true, false, "FLAKY")).isEqualTo("QUARANTINED");
    }
}
