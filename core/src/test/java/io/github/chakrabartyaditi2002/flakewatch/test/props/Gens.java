package io.github.chakrabartyaditi2002.flakewatch.test.props;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SplittableRandom;

public final class Gens {

    private Gens() {}

    public static Generator<Integer> integers(int minInclusive, int maxInclusive) {
        if (minInclusive > maxInclusive) {
            throw new IllegalArgumentException("min must be <= max");
        }
        return rnd -> (minInclusive == maxInclusive)
                ? minInclusive
                : rnd.nextInt(minInclusive, maxInclusive + 1);
    }

    public static Generator<Long> longs(long minInclusive, long maxInclusive) {
        if (minInclusive > maxInclusive) {
            throw new IllegalArgumentException("min must be <= max");
        }
        return rnd -> (minInclusive == maxInclusive)
                ? minInclusive
                : rnd.nextLong(minInclusive, maxInclusive + 1);
    }

    public static Generator<Boolean> booleans() {
        return SplittableRandom::nextBoolean;
    }

    public static Generator<Double> doubles(double min, double max) {
        return rnd -> rnd.nextDouble(min, max);
    }

    @SafeVarargs
    public static <T> Generator<T> oneOf(T... values) {
        if (values.length == 0) {
            throw new IllegalArgumentException("Must provide at least one value");
        }
        return rnd -> values[rnd.nextInt(values.length)];
    }

    public static <T> Generator<T> oneOf(List<T> values) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Must provide non-empty list");
        }
        return rnd -> values.get(rnd.nextInt(values.size()));
    }

    @SafeVarargs
    public static <T> Generator<T> oneOfGenerators(Generator<T>... generators) {
        if (generators.length == 0) {
            throw new IllegalArgumentException("Must provide at least one generator");
        }
        return rnd -> generators[rnd.nextInt(generators.length)].generate(rnd);
    }

    public static Generator<String> strings(int minLen, int maxLen, String alphabet) {
        return rnd -> {
            int len = (minLen == maxLen) ? minLen : rnd.nextInt(minLen, maxLen + 1);
            StringBuilder sb = new StringBuilder(len);
            for (int i = 0; i < len; i++) {
                sb.append(alphabet.charAt(rnd.nextInt(alphabet.length())));
            }
            return sb.toString();
        };
    }

    public static Generator<String> asciiStrings(int minLen, int maxLen) {
        String ascii = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_-";
        return strings(minLen, maxLen, ascii);
    }

    public static <T> Generator<List<T>> lists(Generator<T> elementGen, int minSize, int maxSize) {
        return rnd -> {
            int size = (minSize == maxSize) ? minSize : rnd.nextInt(minSize, maxSize + 1);
            List<T> list = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                list.add(elementGen.generate(rnd));
            }
            return list;
        };
    }

    public static <T> List<T> shuffle(List<T> input, SplittableRandom rnd) {
        List<T> copy = new ArrayList<>(input);
        for (int i = copy.size() - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            Collections.swap(copy, i, j);
        }
        return copy;
    }
}
