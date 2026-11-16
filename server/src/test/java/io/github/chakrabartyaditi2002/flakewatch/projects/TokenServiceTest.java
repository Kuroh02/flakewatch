package io.github.chakrabartyaditi2002.flakewatch.projects;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.chakrabartyaditi2002.flakewatch.platform.random.RandomSource;
import io.github.chakrabartyaditi2002.flakewatch.platform.time.MutableClock;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TokenServiceTest {

    private final TokenService service =
            new TokenService(null, RandomSource.seeded(42), MutableClock.of(Instant.parse("2026-11-16T12:00:00Z")));

    @Test
    @DisplayName("NFR-SEC-001: tokens carry a readable prefix and 256 random bits in base62")
    void tokenFormat() {
        Secret ingest = service.generate(TokenScope.INGEST);
        Secret read = service.generate(TokenScope.READ);
        assertThat(ingest.value()).matches("fw_ing_[0-9A-Za-z]{43}");
        assertThat(read.value()).matches("fw_read_[0-9A-Za-z]{43}");
        assertThat(ingest.value()).isNotEqualTo(service.generate(TokenScope.INGEST).value());
    }

    @Test
    @DisplayName("NFR-SEC-001: a token never shows up when logged or printed by accident")
    void secretIsRedacted() {
        Secret token = service.generate(TokenScope.INGEST);
        assertThat(token.toString()).doesNotContain(token.value());
        assertThat("uploading with " + token).doesNotContain("fw_ing_");
    }

    @Test
    @DisplayName("NFR-SEC-001: only the SHA-256 hash is stored, and the same token always hashes the same way")
    void hashing() {
        byte[] a = TokenService.hash("fw_ing_abc");
        assertThat(a.length).isEqualTo(32);
        assertThat(Arrays.equals(a, TokenService.hash("fw_ing_abc"))).isTrue();
        assertThat(Arrays.equals(a, TokenService.hash("fw_ing_abd"))).isFalse();
    }

    @Test
    @DisplayName("FR-PRJ-005: the Authorization header is read the way CI tools send it")
    void bearerHeader() {
        assertThat(TokenService.bearer("Bearer fw_ing_abc")).isEqualTo("fw_ing_abc");
        assertThat(TokenService.bearer("bearer   fw_ing_abc ")).isEqualTo("fw_ing_abc");
        assertThat(TokenService.bearer("Basic dXNlcjpwYXNz")).isNull();
        assertThat(TokenService.bearer("Bearer ")).isNull();
        assertThat(TokenService.bearer(null)).isNull();
    }

    @Test
    @DisplayName("NFR-SEC-001: base62 keeps every token the same length, even when the random bytes start with zeros")
    void base62FixedLength() {
        assertThat(TokenService.base62(new byte[32])).isEqualTo("0".repeat(43));
        byte[] max = new byte[32];
        Arrays.fill(max, (byte) 0xff);
        assertThat(TokenService.base62(max)).hasSize(43);
    }
}
