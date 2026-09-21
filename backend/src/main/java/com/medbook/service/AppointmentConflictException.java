package com.medbook.service;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Thrown when the requested appointment window overlaps an appointment that
 * already exists for the same doctor and is still blocking the slot.
 *
 * <p>Cancelled appointments never block a slot, so re-booking a cancelled time
 * is allowed.</p>
 */
public class AppointmentConflictException extends RuntimeException {

    public AppointmentConflictException(Long doctorId, LocalDate date, LocalTime startTime, LocalTime endTime) {
        super("Doctor " + doctorId + " already has an appointment overlapping "
                + date + " " + startTime + "-" + endTime);
    }
}
