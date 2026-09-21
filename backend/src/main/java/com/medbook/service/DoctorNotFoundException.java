package com.medbook.service;

public class DoctorNotFoundException extends RuntimeException {

    /** Thrown when no doctor row exists for the supplied ID. */

    public DoctorNotFoundException(Long doctorId) {
        super("Doctor not found with id: " + doctorId);
    }
}