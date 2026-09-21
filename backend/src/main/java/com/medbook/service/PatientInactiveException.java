package com.medbook.service;

/**
 * Thrown when a booking is attempted for a patient whose profile is deactivated
 * ({@code patients.is_active = FALSE}).
 *
 * <p>Kept separate from {@link PatientNotFoundException} so the caller can
 * distinguish a missing patient from an inactive one.</p>
 */
public class PatientInactiveException extends RuntimeException {

    public PatientInactiveException(Long patientId) {
        super("Patient is inactive and cannot book appointments: " + patientId);
    }
}
