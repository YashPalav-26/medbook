package com.medbook.service;

/**
 * Thrown when an appointment ID does not exist in the database.
 *
 * <p>Used by appointment state changes (for example cancellation) where the
 * appointment must exist before its status can be changed.</p>
 */
public class AppointmentNotFoundException extends RuntimeException {

    public AppointmentNotFoundException(Long appointmentId) {
        super("Appointment not found with id: " + appointmentId);
    }
}
