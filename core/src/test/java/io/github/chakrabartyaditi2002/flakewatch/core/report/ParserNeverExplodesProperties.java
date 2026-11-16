package io.github.chakrabartyaditi2002.flakewatch.core.report;

import io.github.chakrabartyaditi2002.flakewatch.test.props.Generator;
import io.github.chakrabartyaditi2002.flakewatch.test.props.Props;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Random and mutated input gives either a result or a {@link ReportParseException}.
 * Never any other exception (NFR-SEC-002, FR-ING-009).
 */
class ParserNeverExplodesProperties {

    private static void parseSafely(byte[] content) {
        try {
            new ReportParser().parse(List.of(new ReportFile("input.xml", content)));
        } catch (ReportParseException expected) {
            // a clear, user-facing failure is fine
        }
        ReportParser.isJunitReport(content);
    }

    @Test
    @DisplayName("NFR-SEC-002: random bytes never cause an unexpected exception")
    void randomBytes() {
        Generator<byte[]> bytes = rnd -> {
            byte[] b = new byte[rnd.nextInt(0, 400)];
            for (int i = 0; i < b.length; i++) {
                b[i] = (byte) rnd.nextInt(256);
            }
            return b;
        };
        Props.forAll(bytes, ParserNeverExplodesProperties::parseSafely);
    }

    @Test
    @DisplayName("NFR-SEC-002: mutated real fixtures never cause an unexpected exception")
    void mutatedFixtures() {
        Generator<byte[]> mutated = rnd -> {
            byte[] original = Fixtures.bytes(Fixtures.PRODUCER_FIXTURES.get(rnd.nextInt(Fixtures.PRODUCER_FIXTURES.size())));
            byte[] copy = original.clone();
            int edits = rnd.nextInt(1, 10);
            for (int i = 0; i < edits; i++) {
                String noise = "<>&\"'/= x\u0000";
                copy[rnd.nextInt(copy.length)] = (byte) noise.charAt(rnd.nextInt(noise.length()));
            }
            int cut = rnd.nextInt(1, copy.length + 1);
            return Arrays.copyOf(copy, cut);
        };
        Props.forAll(mutated, ParserNeverExplodesProperties::parseSafely);
    }
}
