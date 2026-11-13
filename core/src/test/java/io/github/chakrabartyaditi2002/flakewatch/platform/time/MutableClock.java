package io.github.chakrabartyaditi2002.flakewatch.platform.time;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A mutable {@link Clock} for deterministic testing.
 * All mutations are thread-safe.
 */
public final class MutableClock extends Clock {

    private final AtomicReference<Instant> currentInstant;
    private final ZoneId zone;

    public MutableClock(Instant initialInstant, ZoneId zone) {
        this.currentInstant = new AtomicReference<>(Objects.requireNonNull(initialInstant, "initialInstant"));
        this.zone = Objects.requireNonNull(zone, "zone");
    }

    public MutableClock(Instant initialInstant) {
        this(initialInstant, ZoneOffset.UTC);
    }

    public static MutableClock epoch() {
        return new MutableClock(Instant.EPOCH);
    }

    public static MutableClock of(Instant instant) {
        return new MutableClock(instant);
    }

    public static MutableClock of(Instant instant, ZoneId zone) {
        return new MutableClock(instant, zone);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        if (zone.equals(this.zone)) {
            return this;
        }
        return new MutableClock(currentInstant.get(), zone);
    }

    @Override
    public Instant instant() {
        return currentInstant.get();
    }

    public void set(Instant instant) {
        this.currentInstant.set(Objects.requireNonNull(instant, "instant"));
    }

    public void advance(Duration duration) {
        Objects.requireNonNull(duration, "duration");
        this.currentInstant.updateAndGet(current -> current.plus(duration));
    }

    public void tick(Duration duration) {
        advance(duration);
    }
}
