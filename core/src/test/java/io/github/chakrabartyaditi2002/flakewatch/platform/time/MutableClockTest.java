package io.github.chakrabartyaditi2002.flakewatch.platform.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MutableClockTest {

    @Test
    @DisplayName("NFR-QUAL-002: MutableClock starts at provided instant and advances deterministically")
    void startsAtProvidedInstantAndAdvances() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        MutableClock clock = MutableClock.of(start);

        assertThat(clock.instant()).isEqualTo(start);
        assertThat(clock.millis()).isEqualTo(start.toEpochMilli());
        assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);

        clock.advance(Duration.ofMinutes(5));
        assertThat(clock.instant()).isEqualTo(Instant.parse("2026-01-01T00:05:00Z"));

        clock.tick(Duration.ofSeconds(30));
        assertThat(clock.instant()).isEqualTo(Instant.parse("2026-01-01T00:05:30Z"));

        Instant newTime = Instant.parse("2026-06-01T12:00:00Z");
        clock.set(newTime);
        assertThat(clock.instant()).isEqualTo(newTime);
    }

    @Test
    @DisplayName("NFR-QUAL-002: MutableClock supports epoch factory and zone conversion")
    void supportsEpochAndZone() {
        MutableClock clock = MutableClock.epoch();
        assertThat(clock.instant()).isEqualTo(Instant.EPOCH);

        ZoneId tokyo = ZoneId.of("Asia/Tokyo");
        MutableClock tokyoClock = (MutableClock) clock.withZone(tokyo);
        assertThat(tokyoClock.getZone()).isEqualTo(tokyo);
        assertThat(tokyoClock.instant()).isEqualTo(Instant.EPOCH);
    }
}
