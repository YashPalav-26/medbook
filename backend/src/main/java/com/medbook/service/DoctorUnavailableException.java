package com.medbook.service;

import java.time.LocalDate;
import java.time.LocalTime;

public class DoctorUnavailableException extends RuntimeException {

    public DoctorUnavailableException(Long doctorId, LocalDate date, LocalTime startTime, LocalTime endTime) {
        super("Doctor " + doctorId + " is not available on " + date
                + " between " + startTime + " and " + endTime);
    }
}
