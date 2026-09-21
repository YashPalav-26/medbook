package com.medbook.service;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Thrown when the requested appointment window is not covered by any of the
 * doctor's {@link com.medbook.entity.DoctorAvailability} records.
 *
 * <p>Typical causes: no availability for that day of the week, a date outside
 * {@code effectiveFrom}/{@code effectiveUntil}, or a time slot that reaches
 * outside the configured {@code startTime}-{@code endTime} window.</p>
 */
public class DoctorUnavailableException extends RuntimeException {

    public DoctorUnavailableException(Long doctorId, LocalDate date, LocalTime startTime, LocalTime endTime) {
        super("Doctor " + doctorId + " is not available on " + date
                + " between " + startTime + " and " + endTime);
    }
}
