package io.github.chakrabartyaditi2002.flakewatch.platform.error;

import java.util.List;

/** A 400 response that lists every invalid field (FR-ING-008, PLAN §12.1). */
public class ValidationFailedException extends RuntimeException {

    private final transient List<GlobalExceptionHandler.FieldError> errors;

    public ValidationFailedException(List<GlobalExceptionHandler.FieldError> errors) {
        super("Validation failed: " + errors);
        this.errors = List.copyOf(errors);
    }

    public List<GlobalExceptionHandler.FieldError> getErrors() {
        return errors;
    }
}
