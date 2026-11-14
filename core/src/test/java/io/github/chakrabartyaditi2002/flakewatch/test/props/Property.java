package io.github.chakrabartyaditi2002.flakewatch.test.props;

@FunctionalInterface
public interface Property<T> {
    void test(T input) throws Throwable;
}
