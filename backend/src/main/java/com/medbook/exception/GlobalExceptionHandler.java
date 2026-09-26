package com.medbook.exception;

import com.medbook.dto.ErrorResponse;
import com.medbook.service.AppointmentConflictException;
import com.medbook.service.AppointmentNotFoundException;
import com.medbook.service.AvailabilityNotFoundException;
import com.medbook.service.DuplicateAvailabilitySlotException;
import com.medbook.service.DuplicateLicenseNumberException;
import com.medbook.service.DuplicateSpecialtyNameException;
import com.medbook.service.SpecialtyNotFoundException;
import com.medbook.service.AccountInactiveException;
import com.medbook.service.AccountLockedException;
import com.medbook.service.DoctorInactiveException;
import com.medbook.service.DoctorNotFoundException;
import com.medbook.service.DoctorUnavailableException;
import com.medbook.service.EmailAlreadyRegisteredException;
import com.medbook.service.InvalidAppointmentStatusException;
import com.medbook.service.InvalidCredentialsException;
import com.medbook.service.NotificationNotFoundException;
import com.medbook.service.PatientInactiveException;
import com.medbook.service.PatientNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

/**
 * Translates the exceptions thrown by the service layer (and by request handling) into HTTP
 * responses with a consistent {@link ErrorResponse} body.
 *
 * <p>A {@code @RestControllerAdvice} is a global "catch and translate" hook for controllers:
 * controllers stay free of try/catch blocks and the error format stays identical for every
 * endpoint.</p>
 *
 * <h3>Status mapping</h3>
 * <ul>
 * <li><strong>404 Not Found</strong> — the request refers to a row that does not exist:
 *     DoctorNotFoundException, PatientNotFoundException, AppointmentNotFoundException.</li>
 * <li><strong>409 Conflict</strong> — the request is valid but the current state of the data
 *     forbids it: DoctorInactiveException, PatientInactiveException,
 *     DoctorUnavailableException, AppointmentConflictException,
 *     InvalidAppointmentStatusException.</li>
 * <li><strong>400 Bad Request</strong> — the request itself is wrong:
 *     MethodArgumentNotValidException (Bean Validation), IllegalArgumentException (service
 *     value checks), HttpMessageNotReadableException (missing/malformed JSON),
 *     MethodArgumentTypeMismatchException (e.g. {@code /appointments/abc}).</li>
 * </ul>
 *
 * <p>No stack trace or internal exception text is ever sent to the client, and unmapped
 * exceptions keep Spring's default handling ({@code 500}) rather than being swallowed here.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Missing rows: the request refers to something that does not exist.
     */
    @ExceptionHandler({
            DoctorNotFoundException.class,
            PatientNotFoundException.class,
            AppointmentNotFoundException.class,
            SpecialtyNotFoundException.class,
            AvailabilityNotFoundException.class,
            NotificationNotFoundException.class
    })
    public ResponseEntity<ErrorResponse> handleNotFound(RuntimeException exception, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, exception, request);
    }

    /**
     * Business conflicts: the request is well formed, but the current state of the data does
     * not allow it.
     */
    @ExceptionHandler({
            DoctorInactiveException.class,
            PatientInactiveException.class,
            DoctorUnavailableException.class,
            AppointmentConflictException.class,
            InvalidAppointmentStatusException.class,
            DuplicateSpecialtyNameException.class,
            DuplicateLicenseNumberException.class,
            DuplicateAvailabilitySlotException.class
    })
    public ResponseEntity<ErrorResponse> handleBusinessConflict(RuntimeException exception, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, exception, request);
    }

    /**
     * Authenticated but forbidden: the caller's roles/ownership do not allow this
     * operation on this resource. 403 — see {@link com.medbook.security.MedbookAccessDeniedHandler}
     * for the framework-level equivalent.
     */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(org.springframework.security.access.AccessDeniedException exception,
                                                            HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, exception, request);
    }

    /**
     * Login failures: wrong credentials, or an account that is locked or deactivated.
     * All of them are 401 — login never succeeded, so there is nothing to "forbid" yet.
     * The locked/inactive messages only reach this point after the password matched inside
     * {@link com.medbook.service.AuthService#login}, so the account's existence is never
     * disclosed to someone who does not know the credentials.
     */
    @ExceptionHandler({
            InvalidCredentialsException.class,
            AccountLockedException.class,
            AccountInactiveException.class
    })
    public ResponseEntity<ErrorResponse> handleAuthenticationFailure(RuntimeException exception,
                                                                     HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, exception, request);
    }

    /**
     * Registration with an email that already has an account. Unlike the login failures,
     * telling the caller is unavoidable here — the email must be unique.
     */
    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateEmail(EmailAlreadyRegisteredException exception,
                                                              HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, exception, request);
    }

    /**
     * Invalid values detected by the service itself (for example a date in the past or a
     * start time that is not before the end time).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException exception,
                                                               HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, exception, request);
    }

    /**
     * Bean Validation failures on a {@code @Valid @RequestBody}: report which fields were
     * rejected and why.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationFailure(MethodArgumentNotValidException exception,
                                                                 HttpServletRequest request) {
        List<ErrorResponse.FieldError> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ErrorResponse.FieldError(error.getField(), error.getDefaultMessage()))
                .toList();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse.of(
                HttpStatus.BAD_REQUEST,
                "Request validation failed",
                request.getRequestURI(),
                fieldErrors
        ));
    }

    /**
     * Unreadable request body: missing body, malformed JSON, or an unparsable date/time such
     * as {@code "2026-13-45"}. The internal parser message is not echoed back.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException exception,
                                                              HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse.of(
                HttpStatus.BAD_REQUEST,
                "Request body is missing or malformed",
                request.getRequestURI()
        ));
    }

    /**
     * Wrong path variable type, for example {@code GET /api/v1/appointments/abc}.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException exception,
                                                            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse.of(
                HttpStatus.BAD_REQUEST,
                "Invalid value '" + exception.getValue() + "' for parameter '" + exception.getName() + "'",
                request.getRequestURI()
        ));
    }

    private static ResponseEntity<ErrorResponse> build(HttpStatus status, Exception exception,
                                                       HttpServletRequest request) {
        return ResponseEntity.status(status).body(
                ErrorResponse.of(status, exception.getMessage(), request.getRequestURI()));
    }
}
