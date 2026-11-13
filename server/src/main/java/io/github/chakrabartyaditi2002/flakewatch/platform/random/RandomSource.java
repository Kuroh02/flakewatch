package io.github.chakrabartyaditi2002.flakewatch.platform.random;

import java.util.UUID;

public interface RandomSource {

    int nextInt();

    int nextInt(int bound);

    long nextLong();

    long nextLong(long bound);

    double nextDouble();

    double nextDouble(double origin, double bound);

    void nextBytes(byte[] bytes);

    UUID nextUuid();

    static RandomSource seeded(long seed) {
        return new SeededRandomSource(seed);
    }
}
