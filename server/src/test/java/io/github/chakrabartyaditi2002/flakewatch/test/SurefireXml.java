package io.github.chakrabartyaditi2002.flakewatch.test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a Maven Surefire report for multi-day scenarios, where one fixed fixture isn't enough.
 *
 * <p>It writes only the element shapes found in the real fixture
 * {@code core/src/test/resources/fixtures/maven-surefire/TEST-sample.SampleTest.xml}: a plain
 * {@code <testcase>}, {@code <failure>} with a stack trace, {@code <skipped>}, and
 * {@code <flakyFailure>}/{@code <rerunFailure>} with a nested {@code <stackTrace>}.
 */
public final class SurefireXml {

    private final String suite;
    private final List<String> cases = new ArrayList<>();

    private SurefireXml(String suite) {
        this.suite = suite;
    }

    public static SurefireXml suite(String className) {
        return new SurefireXml(className);
    }

    public SurefireXml passed(String test) {
        cases.add(open(test) + "/>");
        return this;
    }

    public SurefireXml failed(String test, String message) {
        cases.add(open(test) + ">" + failure("failure", message) + "</testcase>");
        return this;
    }

    public SurefireXml skipped(String test) {
        cases.add(open(test) + "><skipped message=\"disabled\"/></testcase>");
        return this;
    }

    /** Failed, then passed when Surefire re-ran it ({@code rerunFailingTestsCount=1}). */
    public SurefireXml flaky(String test, String message) {
        cases.add(open(test) + ">" + nested("flakyFailure", message) + "</testcase>");
        return this;
    }

    /** Failed, and failed again on the re-run. */
    public SurefireXml failedTwice(String test, String message) {
        cases.add(open(test) + ">" + failure("failure", message) + nested("rerunFailure", message) + "</testcase>");
        return this;
    }

    public byte[] bytes() {
        return xml().getBytes(StandardCharsets.UTF_8);
    }

    public String fileName() {
        return "TEST-" + suite + ".xml";
    }

    public String xml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<testsuite name=\"" + suite + "\" tests=\"" + cases.size()
                + "\">\n  " + String.join("\n  ", cases) + "\n</testsuite>\n";
    }

    private String open(String test) {
        return "<testcase name=\"" + test + "\" classname=\"" + suite + "\" time=\"0.120\"";
    }

    private String failure(String element, String message) {
        return "<" + element + " message=\"" + message + "\" type=\"org.opentest4j.AssertionFailedError\">"
                + "org.opentest4j.AssertionFailedError: " + message + "\n\tat " + suite + ".check(" + simpleName()
                + ".java:42)\n</" + element + ">";
    }

    private String nested(String element, String message) {
        return "<" + element + " message=\"" + message + "\" type=\"org.opentest4j.AssertionFailedError\">"
                + "<stackTrace>org.opentest4j.AssertionFailedError: " + message + "\n\tat " + suite + ".check("
                + simpleName() + ".java:42)\n</stackTrace></" + element + ">";
    }

    private String simpleName() {
        return suite.substring(suite.lastIndexOf('.') + 1);
    }
}
