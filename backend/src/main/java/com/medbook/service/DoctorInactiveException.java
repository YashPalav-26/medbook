package com.medbook.service;

/**
 * Thrown when a booking is attempted for a doctor whose profile is deactivated
 * ({@code doctors.is_active = FALSE}).
 *
 * <p>An inactive doctor still exists in the database, so this is deliberately a
 * different exception from {@link DoctorNotFoundException}: the caller can tell
 * "no such doctor" apart from "this doctor no longer accepts appointments".</p>
 */
public class DoctorInactiveException extends RuntimeException {

    public DoctorInactiveException(Long doctorId) {
        super("Doctor is inactive and cannot accept appointments: " + doctorId);
    }
}
