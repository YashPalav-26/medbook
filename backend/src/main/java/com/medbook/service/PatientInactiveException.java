package com.medbook.service;








public class PatientInactiveException extends RuntimeException {

    public PatientInactiveException(Long patientId) {
        super("Patient is inactive and cannot book appointments: " + patientId);
    }
}
