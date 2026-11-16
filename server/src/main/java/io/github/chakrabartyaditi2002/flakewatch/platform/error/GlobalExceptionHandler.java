package io.github.chakrabartyaditi2002.flakewatch.platform.error;

import io.github.chakrabartyaditi2002.flakewatch.platform.logging.RequestIdFilter;
import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final URI ABOUT_BLANK = URI.create("about:blank");

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
        complete(problemDetail);

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
        complete(problemDetail);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemDetail);
    }

    @ExceptionHandler(ValidationFailedException.class)
    public ResponseEntity<ProblemDetail> handleValidationFailed(ValidationFailedException ex) {
        log.warn("Validation failed: {}", ex.getErrors());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        problemDetail.setProperty("code", "validation-error");
        problemDetail.setProperty("errors", ex.getErrors());
        complete(problemDetail);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemDetail);
    }

    /** Spring's own request errors keep their 4xx status instead of turning into a 500. */
    @ExceptionHandler({
        NoResourceFoundException.class,
        HttpRequestMethodNotSupportedException.class,
        HttpMediaTypeNotSupportedException.class,
        HttpMessageNotReadableException.class,
        MissingServletRequestPartException.class,
        MissingServletRequestParameterException.class,
        MissingRequestHeaderException.class,
        MethodArgumentTypeMismatchException.class,
        MultipartException.class
    })
    public ResponseEntity<ProblemDetail> handleRequestException(Exception ex) {
        HttpStatusCode status;
        if (ex instanceof MaxUploadSizeExceededException) {
            status = HttpStatus.PAYLOAD_TOO_LARGE;
        } else if (ex instanceof ErrorResponse errorResponse) {
            status = errorResponse.getStatusCode();
        } else {
            status = HttpStatus.BAD_REQUEST;
        }
        String code = switch (status.value()) {
            case 404 -> "not-found";
            case 405 -> "method-not-allowed";
            case 413 -> "payload-too-large";
            case 415 -> "unsupported-media-type";
            default -> "bad-request";
        };
        String detail = status.value() == 404 ? "Not found" : rootMessage(ex);
        log.debug("Request error [{}]: {}", code, ex.getMessage());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setProperty("code", code);
        complete(problemDetail);

        return ResponseEntity
                .status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemDetail);
    }

    private static String rootMessage(Exception ex) {
        if (ex instanceof MaxUploadSizeExceededException) {
            return "The upload is larger than the size limit";
        }
        if (ex instanceof HttpMessageNotReadableException) {
            return "The request body could not be read as JSON";
        }
        if (ex instanceof HttpMediaTypeNotSupportedException) {
            return "Unsupported content type. The metadata part must be sent as application/json";
        }
        String message = ex.getMessage();
        return message == null ? "Bad request" : message;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpectedException(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        problemDetail.setProperty("code", "internal-error");
        complete(problemDetail);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemDetail);
    }

    /**
     * Fills the fields every error response carries: {@code type} (Spring 7 leaves it unset, and
     * the contract requires it; RFC 9457 defines "unset" as about:blank) and the request id.
     */
    private void complete(ProblemDetail problemDetail) {
        if (problemDetail.getType() == null) {
            problemDetail.setType(ABOUT_BLANK);
        }
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        if (requestId != null && !requestId.isBlank()) {
            problemDetail.setProperty(RequestIdFilter.MDC_KEY, requestId);
        }
    }
}
