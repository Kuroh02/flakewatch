package io.github.chakrabartyaditi2002.flakewatch.platform.random;

import java.util.Objects;
import java.util.Random;
import java.util.UUID;

public final class SeededRandomSource implements RandomSource {

    private final Random random;

    public SeededRandomSource(long seed) {
        this.random = new Random(seed);
    }

    @Override
    public int nextInt() {
        return random.nextInt();
    }

    @Override
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive: " + bound);
        }
        return random.nextInt(bound);
    }

    @Override
    public long nextLong() {
        return random.nextLong();
    }

    @Override
    public long nextLong(long bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive: " + bound);
        }
        return random.nextLong(bound);
    }

    @Override
    public double nextDouble() {
        return random.nextDouble();
    }

    @Override
    public double nextDouble(double origin, double bound) {
        if (!(origin < bound)) {
            throw new IllegalArgumentException("origin must be less than bound: origin=" + origin + ", bound=" + bound);
        }
        return random.nextDouble(origin, bound);
    }

    @Override
    public void nextBytes(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        random.nextBytes(bytes);
    }

    @Override
    public UUID nextUuid() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return UUID.nameUUIDFromBytes(bytes);
    }
}
