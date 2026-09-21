package com.medbook.service;

/**
 * Thrown when an admin tries to create or change an availability window into one
 * that already exists for the same doctor (same day, start and end — the schema's
 * {@code uk_availability_doctor_day_time} unique key). Mapped to HTTP 409 by
 * {@link com.medbook.exception.GlobalExceptionHandler} via the business-conflict group.
 */
public class DuplicateAvailabilitySlotException extends RuntimeException {

    public DuplicateAvailabilitySlotException() {
        super("This availability window already exists for the doctor");
    }
}
