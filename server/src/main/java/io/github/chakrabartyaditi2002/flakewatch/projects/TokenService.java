package io.github.chakrabartyaditi2002.flakewatch.projects;

import io.github.chakrabartyaditi2002.flakewatch.platform.db.Db;
import io.github.chakrabartyaditi2002.flakewatch.platform.error.ApiException;
import io.github.chakrabartyaditi2002.flakewatch.platform.random.RandomSource;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Project tokens (NFR-SEC-001, FR-PRJ-005, ADR-0009).
 *
 * <ul>
 *   <li>256 bits of randomness, written in base62 after a readable prefix ({@code fw_ing_} or {@code fw_read_}).
 *   <li>Only the SHA-256 hash is stored, so a database leak doesn't leak usable tokens.
 *   <li>The full value is returned once, at creation.
 * </ul>
 */
@Service
public class TokenService {

    private static final String BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final Duration LAST_USED_THROTTLE = Duration.ofMinutes(5);

    private final JdbcClient jdbc;
    private final RandomSource random;
    private final Clock clock;

    public TokenService(JdbcClient jdbc, RandomSource random, Clock clock) {
        this.jdbc = jdbc;
        this.random = random;
        this.clock = clock;
    }

    /** Creates a token and returns its full value. This is the only time the value is available. */
    public Secret issue(long projectId, String name, TokenScope scope) {
        Secret token = generate(scope);
        String visiblePrefix = token.value().substring(0, scope.prefix().length() + 4);
        jdbc.sql("""
                insert into api_tokens (project_id, name, scope, prefix, token_hash, created_at)
                values (:project, :name, :scope, :prefix, :hash, :now)
                """)
                .param("project", projectId)
                .param("name", name)
                .param("scope", scope.name())
                .param("prefix", visiblePrefix)
                .param("hash", hash(token.value()))
                .param("now", Db.ts(clock.instant()))
                .update();
        return token;
    }

    Secret generate(TokenScope scope) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return new Secret(scope.prefix() + base62(bytes));
    }

    /**
     * Checks an {@code Authorization: Bearer ...} header.
     *
     * @throws ApiException 401 when missing, unknown or revoked; 403 when the scope is wrong
     */
    public TokenPrincipal authenticate(String authorizationHeader, TokenScope required) {
        String token = bearer(authorizationHeader);
        if (token == null) {
            throw unauthorized();
        }
        record Row(long id, long projectId, String scope) {}
        Row row = jdbc.sql("""
                select id, project_id, scope from api_tokens
                 where token_hash = :hash and revoked_at is null
                """)
                .param("hash", hash(token))
                .query((rs, i) -> new Row(rs.getLong("id"), rs.getLong("project_id"), rs.getString("scope")))
                .optional()
                .orElseThrow(TokenService::unauthorized);
        TokenScope scope = TokenScope.valueOf(row.scope());
        if (scope != required) {
            throw new ApiException(HttpStatus.FORBIDDEN, "wrong-token-scope",
                    "This token can't be used here. Use a " + required.name().toLowerCase() + " token.");
        }
        Instant now = clock.instant();
        jdbc.sql("""
                update api_tokens set last_used_at = :now
                 where id = :id and (last_used_at is null or last_used_at < :threshold)
                """)
                .param("now", Db.ts(now))
                .param("threshold", Db.ts(now.minus(LAST_USED_THROTTLE)))
                .param("id", row.id())
                .update();
        return new TokenPrincipal(row.id(), row.projectId(), scope);
    }

    static String bearer(String header) {
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = header.substring(7).strip();
        return token.isEmpty() ? null : token;
    }

    static byte[] hash(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }

    static String base62(byte[] bytes) {
        BigInteger n = new BigInteger(1, bytes);
        BigInteger base = BigInteger.valueOf(62);
        StringBuilder sb = new StringBuilder();
        while (n.signum() > 0) {
            BigInteger[] qr = n.divideAndRemainder(base);
            sb.append(BASE62.charAt(qr[1].intValue()));
            n = qr[0];
        }
        // 32 bytes always fit in 43 base62 characters; pad so every token has the same length
        while (sb.length() < 43) {
            sb.append('0');
        }
        return sb.reverse().toString();
    }

    private static ApiException unauthorized() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", "A valid project token is required");
    }
}
