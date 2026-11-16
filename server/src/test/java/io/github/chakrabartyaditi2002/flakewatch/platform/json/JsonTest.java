package io.github.chakrabartyaditi2002.flakewatch.platform.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JsonTest {

    @Test
    @DisplayName("NFR-REL-001: a job payload survives the round trip through the jobs table")
    void jobPayloadRoundTrip() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectId", 7L);
        payload.put("testIds", List.of(1L, 2L, 3000000000L));
        String json = Json.write(payload);
        assertThat(json).isEqualTo("{\"projectId\":7,\"testIds\":[1,2,3000000000]}");
        assertThat(Json.parseObject(json)).isEqualTo(payload);
    }

    @Test
    @DisplayName("FR-DET-007: evidence text with quotes, newlines, backslashes and non-ASCII survives unchanged")
    void evidenceTextIsEscaped() {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("explanation", "Failed \"twice\"\nin C:\\ci\\run — naïve");
        evidence.put("observedAt", Instant.parse("2026-11-16T10:00:00Z"));
        String json = Json.write(List.of(evidence));
        assertThat(Json.parseArray(json).get(0)).isEqualTo(Map.of(
                "explanation", "Failed \"twice\"\nin C:\\ci\\run — naïve",
                "observedAt", "2026-11-16T10:00:00Z"));
    }

    @Test
    @DisplayName("FR-ING-005: parses what PostgreSQL returns for jsonb, including spacing, nulls and unicode escapes")
    void parsesPostgresOutput() {
        Object parsed = Json.parse("{\"file\": \"a.xml\", \"line\": null, \"ok\": true, \"ratio\": 0.25, \"s\": \"\\u00e9\"}");
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("file", "a.xml");
        expected.put("line", null);
        expected.put("ok", true);
        expected.put("ratio", 0.25);
        expected.put("s", "é");
        assertThat(parsed).isEqualTo(expected);
    }

    @Test
    @DisplayName("Broken JSON is an error, never a silent partial result")
    void rejectsBrokenJson() {
        for (String bad : List.of("{", "[1,", "{\"a\" 1}", "tru", "{\"a\":1} x", "\"\\q\"")) {
            assertThatThrownBy(() -> Json.parse(bad)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
