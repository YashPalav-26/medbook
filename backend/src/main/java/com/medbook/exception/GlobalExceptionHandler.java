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


























@RestControllerAdvice
public class GlobalExceptionHandler {




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






    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(org.springframework.security.access.AccessDeniedException exception,
                                                            HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, exception, request);
    }








    @ExceptionHandler({
            InvalidCredentialsException.class,
            AccountLockedException.class,
            AccountInactiveException.class
    })
    public ResponseEntity<ErrorResponse> handleAuthenticationFailure(RuntimeException exception,
                                                                     HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, exception, request);
    }





    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateEmail(EmailAlreadyRegisteredException exception,
                                                              HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, exception, request);
    }





    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException exception,
                                                               HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, exception, request);
    }





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





    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException exception,
                                                              HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse.of(
                HttpStatus.BAD_REQUEST,
                "Request body is missing or malformed",
                request.getRequestURI()
        ));
    }




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
