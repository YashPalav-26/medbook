package com.medbook.service;

import org.springframework.security.access.AccessDeniedException;

/**
 * Thrown when the authenticated caller is not allowed to touch the requested appointment.
 *
 * <p>Reuses Spring Security's {@link AccessDeniedException} so the failure flows through the
 * same 403 path as role-based rejections: {@link com.medbook.exception.GlobalExceptionHandler}
 * translates it into the standard {@code ErrorResponse} body with HTTP 403. This is the
 * server-side enforcement of the ownership rules — hiding UI elements in React is never the
 * security boundary.</p>
 *
 * <p>The message intentionally does <em>not</em> repeat the appointment ID so a rejected
 * caller learns nothing about which appointments exist.</p>
 */
public class AppointmentOwnershipException extends AccessDeniedException {

    public AppointmentOwnershipException(String message) {
        super(message);
    }
}
