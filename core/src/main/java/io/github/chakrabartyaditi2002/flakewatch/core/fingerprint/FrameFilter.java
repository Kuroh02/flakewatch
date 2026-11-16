package io.github.chakrabartyaditi2002.flakewatch.core.fingerprint;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Picks the top application stack frames from a Java or JavaScript stack trace (FR-GRP-001).
 * Line numbers are dropped (they change with every edit) and framework frames are skipped.
 */
public final class FrameFilter {

    public static final int TOP_FRAMES = 3;

    /** {@code at app//org.Foo.bar(Foo.java:12)} or {@code at java.base/java.lang.Thread.run(Thread.java:1)}. */
    private static final Pattern JAVA_FRAME =
            Pattern.compile("^\\s*at\\s+(?:[\\w.$-]+/+)*([\\w$.<>-]+)\\(([^)]*)\\)\\s*$");

    /** {@code at fn (C:\x\file.ts:12:5)} or {@code at C:\x\file.ts:12:5}. */
    private static final Pattern JS_FRAME_WITH_FN = Pattern.compile("^\\s*at\\s+(.+?)\\s+\\((.+)\\)\\s*$");

    private static final Pattern JS_FRAME_BARE = Pattern.compile("^\\s*at\\s+(\\S+)\\s*$");
    private static final Pattern LINE_COLUMN = Pattern.compile("(?::\\d+)+$");

    private static final List<String> FRAMEWORK_PREFIXES = List.of(
            "java.",
            "javax.",
            "jdk.",
            "sun.",
            "com.sun.",
            "org.junit.",
            "junit.",
            "org.testng.",
            "org.springframework.test.",
            "org.apache.maven.",
            "org.gradle.",
            "org.opentest4j.",
            "org.assertj.",
            "worker.org.gradle.");

    private FrameFilter() {}

    public static List<String> topFrames(String stackTrace) {
        List<String> frames = new ArrayList<>();
        if (stackTrace == null) {
            return frames;
        }
        for (String line : stackTrace.split("\\R")) {
            String frame = parse(line);
            if (frame != null) {
                frames.add(frame);
                if (frames.size() == TOP_FRAMES) {
                    break;
                }
            }
        }
        return frames;
    }

    /** Returns a normalised frame, or null when the line isn't an application frame. */
    static String parse(String line) {
        Matcher java = JAVA_FRAME.matcher(line);
        if (java.matches() && java.group(1).contains(".")) {
            String method = java.group(1);
            for (String prefix : FRAMEWORK_PREFIXES) {
                if (method.startsWith(prefix)) {
                    return null;
                }
            }
            String file = java.group(2);
            int colon = file.indexOf(':');
            String fileName = colon >= 0 ? file.substring(0, colon) : file;
            if (fileName.contains("/") || fileName.contains("\\")) {
                return jsFrame(method, file);
            }
            return method + "(" + fileName + ")";
        }
        Matcher withFn = JS_FRAME_WITH_FN.matcher(line);
        if (withFn.matches()) {
            return jsFrame(withFn.group(1), withFn.group(2));
        }
        Matcher bare = JS_FRAME_BARE.matcher(line);
        if (bare.matches() && LINE_COLUMN.matcher(bare.group(1)).find()) {
            return jsFrame("<anonymous>", bare.group(1));
        }
        return null;
    }

    private static String jsFrame(String function, String location) {
        String loc = location.replace('\\', '/');
        if (loc.startsWith("node:") || loc.contains("/node_modules/") || loc.equals("<anonymous>")) {
            return null;
        }
        loc = LINE_COLUMN.matcher(loc).replaceAll("");
        int slash = loc.lastIndexOf('/');
        String fileName = slash >= 0 ? loc.substring(slash + 1) : loc;
        return function.strip() + " (" + fileName + ")";
    }
}
