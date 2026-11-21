package io.github.chakrabartyaditi2002.flakewatch.core.detection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DetectionSettingsTest {

    @Test
    @DisplayName("FR-DET-011: the defaults are a 14-day window, 2 recoveries for Suspected, 3 failures for Broken")
    void defaults() {
        assertThat(DetectionSettings.defaults()).isEqualTo(new DetectionSettings(14, 2, 3));
    }

    @Test
    @DisplayName("FR-DET-011: the edges of every safe limit are allowed")
    void edgesAreAllowed() {
        assertThat(new DetectionSettings(7, 2, 2)).isNotNull();
        assertThat(new DetectionSettings(60, 5, 10)).isNotNull();
    }

    @ParameterizedTest(name = "{0} = {1}")
    @CsvSource({
        "windowDays, 6, 2, 3",
        "windowDays, 61, 2, 3",
        "suspectedRecoveries, 14, 1, 3",
        "suspectedRecoveries, 14, 6, 3",
        "brokenStreak, 14, 2, 1",
        "brokenStreak, 14, 2, 11",
    })
    @DisplayName("FR-DET-011: a setting just outside its safe limit is refused, naming the setting")
    void justOutsideIsRefused(String setting, int windowDays, int recoveries, int brokenStreak) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DetectionSettings(windowDays, recoveries, brokenStreak))
                .withMessageStartingWith(setting + " must be between");
    }
}
