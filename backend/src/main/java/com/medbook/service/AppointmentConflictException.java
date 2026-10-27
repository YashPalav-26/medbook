package com.medbook.service;

import java.time.LocalDate;
import java.time.LocalTime;

public class AppointmentConflictException extends RuntimeException {

    public AppointmentConflictException(Long doctorId, LocalDate date, LocalTime startTime, LocalTime endTime) {
        super("Doctor " + doctorId + " already has an appointment overlapping "
                + date + " " + startTime + "-" + endTime);
    }
}
