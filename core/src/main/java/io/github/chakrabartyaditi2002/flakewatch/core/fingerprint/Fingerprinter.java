package io.github.chakrabartyaditi2002.flakewatch.core.fingerprint;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Groups similar failures (FR-GRP-001):
 *
 * <pre>
 * fingerprint = SHA-256(failureType + "\n" + normalise(firstLineOfMessage) + "\n" + join(topFrames(3), "\n"))
 * </pre>
 */
public final class Fingerprinter {

    /** An exception class at the start of a stack trace, e.g. {@code java.lang.IllegalStateException: boom}. */
    private static final Pattern LEADING_CLASS = Pattern.compile("^\\s*((?:[\\w$]+\\.)+[\\w$]*(?:Exception|Error|Throwable|Failure)[\\w$]*)\\b");

    private Fingerprinter() {}

    public static Fingerprint fingerprint(String failureType, String message, String stackTrace) {
        String type = resolveType(failureType, stackTrace);
        String normalized = MessageNormalizer.normalize(firstLine(message));
        List<String> frames = FrameFilter.topFrames(stackTrace);
        String material = type + "\n" + normalized + "\n" + String.join("\n", frames);
        return new Fingerprint(sha256(material), type, normalized, frames);
    }

    static String resolveType(String failureType, String stackTrace) {
        if (failureType != null && !failureType.isBlank()) {
            return failureType.strip();
        }
        if (stackTrace != null) {
            Matcher m = LEADING_CLASS.matcher(stackTrace);
            if (m.find()) {
                return m.group(1);
            }
        }
        return "(none)";
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "";
        }
        for (String line : message.split("\\R")) {
            if (!line.isBlank()) {
                return line;
            }
        }
        return "";
    }

    private static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
