package com.example.demo.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.List;

/**
 * Turns exceptions into one consistent JSON error body:
 * {@code {"status":400,"error":"Bad Request","message":"...","timestamp":"...","errors":[...]}}.
 * Expected problems are logged at WARN, unexpected ones at ERROR with a stack trace.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> notFound(NotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), List.of());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> badRequest(IllegalArgumentException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), List.of());
    }

    @ExceptionHandler({ItemUnavailableException.class, InvalidStatusTransitionException.class})
    public ResponseEntity<ApiError> conflictWithBusinessRule(RuntimeException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> invalid(MethodArgumentNotValidException ex) {
        List<String> problems = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + " " + e.getDefaultMessage())
                .toList();
        String message = problems.isEmpty() ? "Validation failed" : String.join("; ", problems);
        return build(HttpStatus.BAD_REQUEST, message, problems);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException ex) {
        return build(HttpStatus.BAD_REQUEST, "Malformed request body", List.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> wrongType(MethodArgumentTypeMismatchException ex) {
        return build(HttpStatus.BAD_REQUEST, "Invalid value for parameter '" + ex.getName() + "'", List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> dataConflict(DataIntegrityViolationException ex) {
        return build(HttpStatus.CONFLICT, "Operation conflicts with existing data", List.of());
    }

    /** Safety net: nothing unexpected may leak a stack trace to the client. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception ex) {
        // Spring MVC's own errors (404 for unknown URLs, 405, 415, ...) already know their status.
        if (ex instanceof ErrorResponse known) {
            HttpStatus status = HttpStatus.resolve(known.getStatusCode().value());
            if (status != null) {
                return build(status, status.getReasonPhrase(), List.of());
            }
        }
        log.error("Unexpected error", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong on the server", List.of());
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, List<String> errors) {
        if (status.is4xxClientError()) {
            log.warn("{} {}", status.value(), message);
        }
        ApiError body = new ApiError(status.value(), status.getReasonPhrase(), message, Instant.now(), errors);
        return ResponseEntity.status(status).body(body);
    }
}
