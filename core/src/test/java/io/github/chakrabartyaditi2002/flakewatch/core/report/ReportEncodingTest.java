package io.github.chakrabartyaditi2002.flakewatch.core.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Reports don't always arrive as plain UTF-8. On Windows agents, a report that passes through
 * PowerShell ({@code Get-Content … > report.xml}, {@code Out-File}) is re-encoded as UTF-16 with a
 * byte-order mark, and many Windows tools add a UTF-8 byte-order mark. The XML declaration still
 * says UTF-8 in both cases. Whatever the encoding, the parsed result must be the same.
 */
class ReportEncodingTest {

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final byte[] UTF16BE_BOM = {(byte) 0xFE, (byte) 0xFF};
    private static final byte[] UTF16LE_BOM = {(byte) 0xFF, (byte) 0xFE};

    static Stream<Arguments> reencoded() {
        return Fixtures.PRODUCER_FIXTURES.stream().flatMap(path -> Stream.of(
                Arguments.of(path, "UTF-8 with a byte-order mark", UTF8_BOM, StandardCharsets.UTF_8),
                Arguments.of(path, "UTF-16LE with a byte-order mark (PowerShell)", UTF16LE_BOM, StandardCharsets.UTF_16LE),
                Arguments.of(path, "UTF-16BE with a byte-order mark", UTF16BE_BOM, StandardCharsets.UTF_16BE)));
    }

    @ParameterizedTest(name = "{0} as {1}")
    @MethodSource("reencoded")
    @DisplayName("FR-ING-006: a real report re-encoded by a Windows tool parses exactly like the original")
    void sameResultWhateverTheEncoding(String path, String how, byte[] bom, Charset charset) {
        ReportFile original = Fixtures.file(path);
        String text = new String(original.content(), StandardCharsets.UTF_8);
        ReportFile reencoded = new ReportFile(original.name(), concat(bom, text.getBytes(charset)));

        ParseResult expected = new ReportParser().parse(List.of(original));
        ParseResult actual = new ReportParser().parse(List.of(reencoded));

        assertThat(expected.testCases()).isNotEmpty();
        assertThat(actual).isEqualTo(expected);
    }

    private static byte[] concat(byte[] prefix, byte[] rest) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(prefix);
        out.writeBytes(rest);
        return out.toByteArray();
    }
}
