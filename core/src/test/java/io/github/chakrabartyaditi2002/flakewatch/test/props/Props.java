package io.github.chakrabartyaditi2002.flakewatch.test.props;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

public final class Props {

    public static final int DEFAULT_CASES = 500;

    private Props() {}

    public static <T> void forAll(Generator<T> generator, Property<T> property) {
        int cases = Integer.getInteger("props.cases", DEFAULT_CASES);
        Long seedProp = Long.getLong("props.seed");
        long seed = (seedProp != null) ? seedProp : System.currentTimeMillis() ^ System.nanoTime();
        forAll(generator, cases, seed, property);
    }

    public static <T> void forAll(Generator<T> generator, int cases, Property<T> property) {
        Long seedProp = Long.getLong("props.seed");
        long seed = (seedProp != null) ? seedProp : System.currentTimeMillis() ^ System.nanoTime();
        forAll(generator, cases, seed, property);
    }

    public static <T> void forAll(Generator<T> generator, int cases, long seed, Property<T> property) {
        SplittableRandom rnd = new SplittableRandom(seed);

        for (int i = 1; i <= cases; i++) {
            T input = generator.generate(rnd);
            try {
                property.test(input);
            } catch (Throwable t) {
                Object shrunk = shrinkIfPossible(input, property);
                String msg = String.format(
                        "Property falsified!\n  props.seed=%d\n  Failed at case %d of %d\n  Original input: %s%s\n  Cause: %s",
                        seed,
                        i,
                        cases,
                        input,
                        (shrunk != null && !shrunk.equals(input)) ? "\n  Shrunk to: " + shrunk : "",
                        t.getMessage());
                throw new PropertyAssertionError(msg, seed, i, input, (shrunk != null) ? shrunk : input, t);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> Object shrinkIfPossible(T input, Property<T> property) {
        if (!(input instanceof List<?> list)) {
            return input;
        }

        List<Object> current = new ArrayList<>(list);
        boolean changed = true;

        while (changed && current.size() > 1) {
            changed = false;
            for (int i = 0; i < current.size(); i++) {
                List<Object> candidate = new ArrayList<>(current);
                candidate.remove(i);
                if (fails((Property<T>) property, (T) candidate)) {
                    current = candidate;
                    changed = true;
                    break;
                }
            }
        }

        return current;
    }

    private static <T> boolean fails(Property<T> property, T input) {
        try {
            property.test(input);
            return false;
        } catch (Throwable ignored) {
            return true;
        }
    }
}
