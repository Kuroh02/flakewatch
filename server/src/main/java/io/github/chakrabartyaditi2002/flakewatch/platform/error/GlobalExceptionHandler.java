package io.github.chakrabartyaditi2002.flakewatch.platform.error;

import io.github.chakrabartyaditi2002.flakewatch.platform.logging.RequestIdFilter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public record FieldError(String field, String message) {}

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApiException(ApiException ex) {
        if (ex.getStatus().is5xxServerError()) {
            log.error("API error [{}]: {}", ex.getCode(), ex.getDetail(), ex);
        } else {
            log.warn("API error [{}]: {}", ex.getCode(), ex.getDetail());
        }

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getDetail());
        problemDetail.setProperty("code", ex.getCode());
        attachRequestId(problemDetail);

        return ResponseEntity
                .status(ex.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemDetail);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationException(MethodArgumentNotValidException ex) {
        log.warn("Validation failed: {}", ex.getMessage());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        problemDetail.setProperty("code", "validation-error");

        List<FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> new FieldError(err.getField(), err.getDefaultMessage()))
                .toList();
        problemDetail.setProperty("errors", fieldErrors);
        attachRequestId(problemDetail);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemDetail);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpectedException(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        problemDetail.setProperty("code", "internal-error");
        attachRequestId(problemDetail);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemDetail);
    }

    private void attachRequestId(ProblemDetail problemDetail) {
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        if (requestId != null && !requestId.isBlank()) {
            problemDetail.setProperty(RequestIdFilter.MDC_KEY, requestId);
        }
    }
}
