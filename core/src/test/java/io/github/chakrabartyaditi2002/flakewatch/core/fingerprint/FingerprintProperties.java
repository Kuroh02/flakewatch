package io.github.chakrabartyaditi2002.flakewatch.core.fingerprint;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.chakrabartyaditi2002.flakewatch.test.props.Generator;
import io.github.chakrabartyaditi2002.flakewatch.test.props.Gens;
import io.github.chakrabartyaditi2002.flakewatch.test.props.Props;
import java.util.SplittableRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** PROP-FP-01 to PROP-FP-05 from PLAN §10. */
class FingerprintProperties {

    private static final Generator<String> MESSAGES = Gens.oneOfGenerators(
            Gens.strings(0, 60, "abcdefxyz ABC0123456789:-./\\_<>"),
            rnd -> "Timeout after " + rnd.nextInt(100_000) + " ms on port " + rnd.nextInt(65536),
            rnd -> "order " + uuid(rnd) + " failed at 2026-11-" + (10 + rnd.nextInt(19)) + "T10:00:00Z",
            rnd -> "file /tmp/run" + rnd.nextInt(1000) + "/x.json missing, id 0x" + Long.toHexString(rnd.nextLong()));

    private static String uuid(SplittableRandom rnd) {
        return String.format("%08x-%04x-4%03x-8%03x-%012x",
                rnd.nextInt() & 0xffffffffL, rnd.nextInt(0x10000), rnd.nextInt(0x1000), rnd.nextInt(0x1000),
                rnd.nextLong() & 0xffffffffffffL);
    }

    /** Replaces every digit with another random digit, keeping everything else (and any digit before an "x"). */
    private static String redigit(String s, SplittableRandom rnd) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean hexPrefix = i + 1 < s.length() && s.charAt(i + 1) == 'x';
            sb.append(Character.isDigit(c) && !hexPrefix ? (char) ('0' + rnd.nextInt(10)) : c);
        }
        return sb.toString();
    }

    @Test
    @DisplayName("PROP-FP-01: normalising twice gives the same result as normalising once")
    void idempotent() {
        Props.forAll(MESSAGES, m -> {
            String once = MessageNormalizer.normalize(m);
            assertThat(MessageNormalizer.normalize(once)).isEqualTo(once);
        });
    }

    @Test
    @DisplayName("PROP-FP-02: messages that differ only in digits share a fingerprint")
    void digitsDoNotMatter() {
        Generator<String[]> pairs = rnd -> {
            String m = MESSAGES.generate(rnd);
            return new String[] {m, redigit(m, rnd)};
        };
        Props.forAll(pairs, p -> assertThat(Fingerprinter.fingerprint("T", p[0], null))
                .isEqualTo(Fingerprinter.fingerprint("T", p[1], null)));
    }

    @Test
    @DisplayName("PROP-FP-03: frames that differ only in line numbers share a fingerprint")
    void lineNumbersDoNotMatter() {
        Generator<int[]> lines = rnd -> new int[] {rnd.nextInt(1, 5000), rnd.nextInt(1, 5000), rnd.nextInt(1, 99)};
        Props.forAll(lines, l -> {
            String a = "x.Boom: y\n\tat shop.Cart.add(Cart.java:" + l[0] + ")\n    at fn (/w/app/cart.ts:" + l[1] + ":" + l[2] + ")";
            String b = "x.Boom: y\n\tat shop.Cart.add(Cart.java:7)\n    at fn (/w/app/cart.ts:1:1)";
            assertThat(Fingerprinter.fingerprint("T", "m", a)).isEqualTo(Fingerprinter.fingerprint("T", "m", b));
        });
    }

    @Test
    @DisplayName("PROP-FP-04: different failure types never share a fingerprint for the same message")
    void typesMatter() {
        Generator<String[]> cases = rnd -> new String[] {
            MESSAGES.generate(rnd), Gens.strings(1, 20, "abcXYZ.$").generate(rnd), Gens.strings(1, 20, "abcXYZ.$").generate(rnd)
        };
        Props.forAll(cases, c -> {
            if (!c[1].strip().equals(c[2].strip())) {
                assertThat(Fingerprinter.fingerprint(c[1], c[0], null)).isNotEqualTo(Fingerprinter.fingerprint(c[2], c[0], null));
            }
        });
    }

    @Test
    @DisplayName("PROP-FP-05: fingerprints are stable across JVMs and locales (pinned values)")
    void pinnedValues() {
        assertThat(Fingerprinter.fingerprint("java.util.concurrent.TimeoutException", "Timeout after 3012 ms",
                        "x\n\tat shop.Client.call(Client.java:40)").hex())
                .isEqualTo(PINNED_TIMEOUT);
        assertThat(Fingerprinter.fingerprint(null, null, null).hex()).isEqualTo(PINNED_EMPTY);
    }

    // SHA-256 of "java.util.concurrent.TimeoutException\nTimeout after <n> ms\nshop.Client.call(Client.java)"
    static final String PINNED_TIMEOUT = "c98ff70e9f92688b9ed5053aecc40c4fad8e2a72a842e4c0fe9076de90ba90b7";
    // SHA-256 of "(none)\n\n"
    static final String PINNED_EMPTY = "0c04036d5c924d84dd116d5b1181d8893ec4406035fd62ef3a6a68d60649858f";
}
