package io.github.chakrabartyaditi2002.flakewatch.platform.error;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.chakrabartyaditi2002.flakewatch.platform.logging.RequestIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @BeforeEach
    void setUp() {
        MDC.put(RequestIdFilter.MDC_KEY, "test-req-999");
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("NFR-OBS-001: handles ApiException returning problem+json with code and requestId")
    void handlesApiException() {
        ApiException ex = new ApiException(
            HttpStatus.CONFLICT,
            "duplicate-upload-different-content",
            "An upload with this idempotency key already exists with different contents"
        );

        ResponseEntity<ProblemDetail> response = handler.handleApiException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ProblemDetail body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(body.getTitle()).isEqualTo("Conflict");
        assertThat(body.getDetail()).isEqualTo("An upload with this idempotency key already exists with different contents");
        assertThat(body.getProperties()).containsEntry("code", "duplicate-upload-different-content");
        assertThat(body.getProperties()).containsEntry("requestId", "test-req-999");
        // The contract makes `type` required. Spring 7 leaves it unset (so it would be left out
        // of the JSON); RFC 9457 says an absent type means about:blank, so we send that explicitly.
        assertThat(body.getType()).hasToString("about:blank");
    }

    @Test
    @DisplayName("NFR-OBS-001: handles unexpected exceptions with internal-error code")
    void handlesUnexpectedException() {
        Exception ex = new RuntimeException("database timeout");

        ResponseEntity<ProblemDetail> response = handler.handleUnexpectedException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        ProblemDetail body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getProperties()).containsEntry("code", "internal-error");
        assertThat(body.getType()).hasToString("about:blank");
        assertThat(body.getProperties()).containsEntry("requestId", "test-req-999");
    }
}
