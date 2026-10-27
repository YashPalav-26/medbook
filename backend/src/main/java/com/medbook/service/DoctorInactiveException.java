package com.medbook.service;

public class DoctorInactiveException extends RuntimeException {

    public DoctorInactiveException(Long doctorId) {
        super("Doctor is inactive and cannot accept appointments: " + doctorId);
    }
}
