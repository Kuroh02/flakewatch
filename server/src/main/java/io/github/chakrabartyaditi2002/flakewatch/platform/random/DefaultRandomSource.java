package io.github.chakrabartyaditi2002.flakewatch.platform.random;

import java.security.SecureRandom;
import java.util.Objects;
import java.util.UUID;

public final class DefaultRandomSource implements RandomSource {

    private final SecureRandom secureRandom;

    public DefaultRandomSource() {
        this.secureRandom = new SecureRandom();
    }

    @Override
    public int nextInt() {
        return secureRandom.nextInt();
    }

    @Override
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive: " + bound);
        }
        return secureRandom.nextInt(bound);
    }

    @Override
    public long nextLong() {
        return secureRandom.nextLong();
    }

    @Override
    public long nextLong(long bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive: " + bound);
        }
        return secureRandom.nextLong(bound);
    }

    @Override
    public double nextDouble() {
        return secureRandom.nextDouble();
    }

    @Override
    public double nextDouble(double origin, double bound) {
        if (!(origin < bound)) {
            throw new IllegalArgumentException("origin must be less than bound: origin=" + origin + ", bound=" + bound);
        }
        return secureRandom.nextDouble(origin, bound);
    }

    @Override
    public void nextBytes(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        secureRandom.nextBytes(bytes);
    }

    @Override
    public UUID nextUuid() {
        return UUID.randomUUID();
    }
}
