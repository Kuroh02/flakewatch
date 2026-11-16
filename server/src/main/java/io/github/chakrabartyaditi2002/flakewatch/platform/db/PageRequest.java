package io.github.chakrabartyaditi2002.flakewatch.platform.db;

import io.github.chakrabartyaditi2002.flakewatch.platform.error.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * Cursor pagination (PLAN §12.1). The cursor is opaque to clients; today it encodes an offset,
 * which can change later without breaking anyone.
 */
public record PageRequest(int limit, int offset) {

    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 100;

    public static PageRequest of(Integer limit, String cursor) {
        int size = limit == null ? DEFAULT_LIMIT : limit;
        if (size < 1 || size > MAX_LIMIT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid-limit", "limit must be between 1 and " + MAX_LIMIT);
        }
        return new PageRequest(size, decode(cursor));
    }

    /** Ask the database for one row more than the page, to know whether a next page exists. */
    public int fetchSize() {
        return limit + 1;
    }

    /** Trims the extra row and returns the cursor for the next page, or null on the last page. */
    public <T> Page<T> toPage(List<T> rows) {
        if (rows.size() > limit) {
            return new Page<>(List.copyOf(rows.subList(0, limit)), encode(offset + limit));
        }
        return new Page<>(List.copyOf(rows), null);
    }

    public record Page<T>(List<T> items, String nextCursor) {}

    static String encode(int offset) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(("o:" + offset).getBytes(StandardCharsets.UTF_8));
    }

    static int decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            if (!raw.startsWith("o:")) {
                throw new IllegalArgumentException();
            }
            int offset = Integer.parseInt(raw.substring(2));
            if (offset < 0) {
                throw new IllegalArgumentException();
            }
            return offset;
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid-cursor", "The cursor is not valid");
        }
    }
}
