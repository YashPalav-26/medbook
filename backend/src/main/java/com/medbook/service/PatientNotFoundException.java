package com.medbook.service;

public class PatientNotFoundException extends RuntimeException {



    public PatientNotFoundException(Long patientId) {
        super("Patient not found with id: " + patientId);
    }
}