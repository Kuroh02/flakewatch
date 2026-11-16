package io.github.chakrabartyaditi2002.flakewatch.core.fingerprint;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FingerprinterTest {

    static List<Arguments> normalisations() {
        return List.of(
                Arguments.of("Timeout after 3012 ms", "Timeout after <n> ms"),
                Arguments.of("Timeout after 3012ms", "Timeout after <n>"),
                Arguments.of("order 3f2a9c1d-1111-4aaa-8bbb-123456789abc not found", "order <uuid> not found"),
                Arguments.of("at 2026-11-14T08:21:40.325Z the job died", "at <time> the job died"),
                Arguments.of("started 11:13:47 then stopped", "started <time> then stopped"),
                Arguments.of("object 0x7ffe12 and hash deadbeefcafe", "object <hex> and hash <hex>"),
                Arguments.of("no file /tmp/junit123/data.json here", "no file <path> here"),
                Arguments.of("cannot open C:\\Users\\Aditi\\AppData\\x.txt", "cannot open <path>"),
                Arguments.of("port   8080\n refused", "port <n> refused"));
    }

    @ParameterizedTest
    @MethodSource("normalisations")
    @DisplayName("FR-GRP-002: messages are normalised")
    void normalises(String input, String expected) {
        assertThat(MessageNormalizer.normalize(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("FR-GRP-002: normalised messages are capped at 500 characters")
    void capsLength() {
        assertThat(MessageNormalizer.normalize("word ".repeat(400))).hasSize(MessageNormalizer.MAX_LENGTH);
    }

    @Test
    @DisplayName("FR-GRP-001: Java frames lose line numbers and framework frames are skipped")
    void javaFrames() {
        String trace = """
                org.opentest4j.AssertionFailedError: boom
                \tat app//org.junit.jupiter.api.AssertionUtils.fail(AssertionUtils.java:42)
                \tat app//sample.Cart.add(Cart.java:22)
                \tat java.base/java.lang.reflect.Method.invoke(Method.java:565)
                \tat sample.CartTest.adds(CartTest.java:12)
                \tat sample.Helper.go(Unknown Source)
                \tat sample.Fourth.never(Fourth.java:1)
                """;
        assertThat(FrameFilter.topFrames(trace))
                .containsExactly("sample.Cart.add(Cart.java)", "sample.CartTest.adds(CartTest.java)", "sample.Helper.go(Unknown Source)");
    }

    @Test
    @DisplayName("FR-GRP-001: JavaScript frames keep the function and file name, and skip node internals and node_modules")
    void javascriptFrames() {
        String trace = """
                Error: expect(received).toBe(expected)
                    at Object.toBe (C:\\work\\shop\\cart.test.js:7:18)
                    at Promise.then.completed (C:\\work\\shop\\node_modules\\jest-circus\\build\\utils.js:298:28)
                    at new Promise (<anonymous>)
                    at processTicksAndRejections (node:internal/process/task_queues:103:5)
                    at /home/ci/shop/tests/cart.spec.ts:8:16
                """;
        assertThat(FrameFilter.topFrames(trace))
                .containsExactly("Object.toBe (cart.test.js)", "<anonymous> (cart.spec.ts)");
    }

    @Test
    @DisplayName("FR-GRP-001: the failure type falls back to the exception class at the start of the stack trace")
    void failureType() {
        assertThat(Fingerprinter.resolveType("java.lang.AssertionError", "x")).isEqualTo("java.lang.AssertionError");
        assertThat(Fingerprinter.resolveType(null, "java.lang.IllegalStateException: boom\n\tat a.B.c(B.java:1)"))
                .isEqualTo("java.lang.IllegalStateException");
        assertThat(Fingerprinter.resolveType("", "Error: plain JavaScript")).isEqualTo("(none)");
        assertThat(Fingerprinter.resolveType(null, null)).isEqualTo("(none)");
    }

    @Test
    @DisplayName("FR-GRP-001: similar timeouts share a group (US-05 scenario 1)")
    void sameGroupForSimilarTimeouts() {
        String stack = "java.util.concurrent.TimeoutException: x\n\tat shop.Client.call(Client.java:40)\n\tat shop.ClientTest.t(ClientTest.java:9)";
        Fingerprint a = Fingerprinter.fingerprint("java.util.concurrent.TimeoutException", "Timeout after 3012 ms", stack);
        Fingerprint b = Fingerprinter.fingerprint(
                "java.util.concurrent.TimeoutException",
                "Timeout after 2987 ms",
                stack.replace("Client.java:40", "Client.java:41"));
        assertThat(a).isEqualTo(b);
    }

    @Test
    @DisplayName("FR-GRP-001: same message but a different exception type is a different group (US-05 scenario 2)")
    void differentTypeDifferentGroup() {
        Fingerprint a = Fingerprinter.fingerprint("java.lang.AssertionError", "Timeout after 3012 ms", null);
        Fingerprint b = Fingerprinter.fingerprint("java.util.concurrent.TimeoutException", "Timeout after 3012 ms", null);
        assertThat(a).isNotEqualTo(b);
    }
}
