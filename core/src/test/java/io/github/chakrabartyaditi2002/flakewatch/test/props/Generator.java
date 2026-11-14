package io.github.chakrabartyaditi2002.flakewatch.test.props;

import java.util.SplittableRandom;
import java.util.function.Function;
import java.util.function.Predicate;

@FunctionalInterface
public interface Generator<T> {

    T generate(SplittableRandom rnd);

    default <R> Generator<R> map(Function<? super T, ? extends R> mapper) {
        return rnd -> mapper.apply(generate(rnd));
    }

    default <R> Generator<R> flatMap(Function<? super T, ? extends Generator<? extends R>> mapper) {
        return rnd -> mapper.apply(generate(rnd)).generate(rnd);
    }

    default Generator<T> filter(Predicate<? super T> predicate) {
        return rnd -> {
            for (int i = 0; i < 1000; i++) {
                T val = generate(rnd);
                if (predicate.test(val)) {
                    return val;
                }
            }
            throw new IllegalStateException(
                    "Failed to generate a valid value matching filter predicate after 1000 attempts");
        };
    }
}
