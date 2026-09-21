package com.medbook.service;

public class PatientNotFoundException extends RuntimeException {

    /** Thrown when no patient row exists for the supplied ID. */

    public PatientNotFoundException(Long patientId) {
        super("Patient not found with id: " + patientId);
    }
}