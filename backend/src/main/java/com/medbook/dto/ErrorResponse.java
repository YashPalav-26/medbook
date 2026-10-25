package com.medbook.dto;

import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;

































public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldError> fieldErrors
) {







    public record FieldError(String field, String message) {
    }









    public static ErrorResponse of(HttpStatus status, String message, String path) {
        return of(status, message, path, List.of());
    }










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
