package com.medbook.service;

import com.medbook.entity.AppointmentStatus;

/**
 * Thrown when a status change would lead the appointment into an impossible or
 * already-finished state (for example cancelling a COMPLETED appointment or
 * cancelling an appointment twice).
 *
 * <p>The allowed statuses are defined by the {@link AppointmentStatus} enum and
 * by the schema CHECK constraint. This exception only guards the
 * <em>transitions</em> between those statuses, not the statuses themselves.</p>
 */
public class InvalidAppointmentStatusException extends RuntimeException {

    public InvalidAppointmentStatusException(AppointmentStatus current, AppointmentStatus requested, String reason) {
        super("Cannot change appointment status from " + current + " to " + requested + ": " + reason);
    }
}
