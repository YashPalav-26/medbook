package com.medbook.dto;

import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Consistent error body returned by
 * {@link com.medbook.exception.GlobalExceptionHandler} for every failed request.
 *
 * <p>Example (a validation failure):</p>
 *
 * <pre>
 * {
 *   "timestamp": "2026-09-21T11:42:07.315",
 *   "status": 400,
 *   "error": "Bad Request",
 *   "message": "Request validation failed",
 *   "path": "/api/v1/appointments",
 *   "fieldErrors": [
 *     { "field": "patientId", "message": "patientId is required" }
 *   ]
 * }
 * </pre>
 *
 * <p>The body deliberately contains no stack trace, no SQL, and no internal class names:
 * clients only receive an HTTP status, a short reason phrase, the safe message written by
 * the service or the validation layer, and — for validation failures — which fields were
 * rejected.</p>
 *
 * @param timestamp   when the error was handled (server time)
 * @param status      HTTP status code, for example {@code 404}
 * @param error       HTTP reason phrase, for example {@code "Not Found"}
 * @param message     human-readable explanation, safe to show to a client
 * @param path        request path that produced the error
 * @param fieldErrors per-field validation problems (empty list when the failure was not a
 *                    field-validation failure)
 */
public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldError> fieldErrors
) {

    /**
     * One rejected request field.
     *
     * @param field   the offending field name, for example {@code "appointmentDate"}
     * @param message the constraint message, for example {@code "appointmentDate is required"}
     */
    public record FieldError(String field, String message) {
    }

    /**
     * Build an error body without field-level details.
     *
     * @param status  the HTTP status to report
     * @param message safe message for the client
     * @param path    request path
     * @return the error body
     */
    public static ErrorResponse of(HttpStatus status, String message, String path) {
        return of(status, message, path, List.of());
    }

    /**
     * Build an error body with field-level details.
     *
     * @param status      the HTTP status to report
     * @param message     safe message for the client
     * @param path        request path
     * @param fieldErrors per-field validation problems
     * @return the error body
     */
    public static ErrorResponse of(HttpStatus status, String message, String path, List<FieldError> fieldErrors) {
        return new ErrorResponse(
                LocalDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                path,
                List.copyOf(fieldErrors)
        );
    }
}
