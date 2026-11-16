package io.github.chakrabartyaditi2002.flakewatch.core.testkey;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Builds the stable names that identify a test across runs (FR-ING-010, GLOSSARY "Test key").
 *
 * <ul>
 *   <li><b>Test key:</b> {@code <suite>#<name>}, whitespace tidied, parameter suffix kept.
 *   <li><b>Method key:</b> the test key without a parameter suffix, e.g. {@code CartTest#adds}.
 * </ul>
 */
public final class TestKeys {

    /** Longest test key we store. Longer keys are cut and get a hash suffix so they stay unique. */
    public static final int MAX_KEY_LENGTH = 2000;

    private TestKeys() {}

    /** Collapses runs of whitespace into one space and trims. Null becomes "". */
    public static String tidy(String s) {
        if (s == null) {
            return "";
        }
        return s.strip().replaceAll("\\s+", " ");
    }

    public static String testKey(String suite, String name) {
        return cap(tidy(suite) + "#" + tidy(name));
    }

    public static String methodKey(String suite, String name) {
        return cap(tidy(suite) + "#" + stripParameters(tidy(name)));
    }

    /** {@code adds(int)[2]} becomes {@code adds}. A name that is only parameters is kept as it is. */
    static String stripParameters(String name) {
        int cut = name.length();
        int paren = name.indexOf('(');
        int bracket = name.indexOf('[');
        if (paren >= 0) {
            cut = paren;
        }
        if (bracket >= 0 && bracket < cut) {
            cut = bracket;
        }
        String stripped = name.substring(0, cut).strip();
        return stripped.isEmpty() ? name : stripped;
    }

    /** SHA-256 of the key, used as the unique database key because keys can be very long. */
    public static byte[] keyHash(String testKey) {
        return sha256(testKey);
    }

    private static String cap(String key) {
        if (key.length() <= MAX_KEY_LENGTH) {
            return key;
        }
        String suffix = "~" + HexFormat.of().formatHex(sha256(key)).substring(0, 8);
        return key.substring(0, MAX_KEY_LENGTH - suffix.length()) + suffix;
    }

    private static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
