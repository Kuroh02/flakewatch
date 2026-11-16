package io.github.chakrabartyaditi2002.flakewatch.core.fingerprint;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Removes the parts of a failure message that change from run to run (FR-GRP-002, PLAN §10),
 * so "Timeout after 3012 ms" and "Timeout after 2987 ms" read the same.
 *
 * <p>Applying it twice gives the same result as applying it once (PROP-FP-01).
 */
public final class MessageNormalizer {

    public static final int MAX_LENGTH = 500;

    private record Rule(Pattern pattern, String replacement) {}

    private static final List<Rule> RULES = List.of(
            new Rule(Pattern.compile("(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b"), "<uuid>"),
            new Rule(
                    Pattern.compile("\\d{4}-\\d{2}-\\d{2}(?:[T ]\\d{2}:\\d{2}(?::\\d{2}(?:[.,]\\d+)?)?(?:Z|[+-]\\d{2}:?\\d{2})?)?"),
                    "<time>"),
            new Rule(Pattern.compile("\\b\\d{1,2}:\\d{2}:\\d{2}(?:[.,]\\d+)?\\b"), "<time>"),
            new Rule(Pattern.compile("(?i)\\b0x[0-9a-f]+\\b"), "<hex>"),
            // 8+ hex characters with at least one letter, so plain numbers stay numbers
            new Rule(Pattern.compile("(?i)\\b(?=[0-9a-f]*[a-f])[0-9a-f]{8,}\\b"), "<hex>"),
            new Rule(
                    Pattern.compile("(?i)(?:/tmp/|/var/folders/|/home/|/Users/|[a-z]:\\\\Users\\\\|[a-z]:\\\\Windows\\\\Temp\\\\)\\S*"),
                    "<path>"),
            new Rule(Pattern.compile("\\d+(?:[.,]\\d+)*(?:ms|s|m|h|ns|us|µs)?"), "<n>"));

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private MessageNormalizer() {}

    public static String normalize(String message) {
        if (message == null) {
            return "";
        }
        String out = message;
        for (Rule rule : RULES) {
            out = rule.pattern().matcher(out).replaceAll(rule.replacement());
        }
        out = WHITESPACE.matcher(out).replaceAll(" ").strip();
        return out.length() > MAX_LENGTH ? out.substring(0, MAX_LENGTH) : out;
    }
}
