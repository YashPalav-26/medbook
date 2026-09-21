package com.medbook.service;

/**
 * Thrown when an admin operation references an availability window that does not
 * exist. Mapped to 404 by {@link com.medbook.exception.GlobalExceptionHandler}
 * via the not-found group.
 */
public class AvailabilityNotFoundException extends RuntimeException {

    public AvailabilityNotFoundException(Long availabilityId) {
        super("Availability window not found with id: " + availabilityId);
    }
}
