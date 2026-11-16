package io.github.chakrabartyaditi2002.flakewatch.core.report;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

/** Loads report fixtures from {@code src/test/resources/fixtures}. Shared with the server tests. */
public final class Fixtures {

    private Fixtures() {}

    public static byte[] bytes(String path) {
        try (InputStream in = Fixtures.class.getResourceAsStream("/fixtures/" + path)) {
            if (in == null) {
                throw new IllegalArgumentException("No fixture at /fixtures/" + path);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static ReportFile file(String path) {
        return new ReportFile(path.substring(path.lastIndexOf('/') + 1), bytes(path));
    }

    public static ParseResult parse(String... paths) {
        return new ReportParser().parse(java.util.Arrays.stream(paths).map(Fixtures::file).toList());
    }

    /** Every real producer fixture (FR-ING-006). */
    public static final List<String> PRODUCER_FIXTURES = List.of(
            "maven-surefire/TEST-sample.SampleTest.xml",
            "gradle/TEST-sample.SampleGradleTest.xml",
            "testng/TEST-sample.SampleTestNGTest.xml",
            "playwright/results.xml",
            "webdriverio/results.xml",
            "jest-junit/junit.xml");
}
