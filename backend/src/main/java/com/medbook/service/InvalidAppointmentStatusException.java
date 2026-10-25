package com.medbook.service;

import com.medbook.entity.AppointmentStatus;










public class InvalidAppointmentStatusException extends RuntimeException {

    public InvalidAppointmentStatusException(AppointmentStatus current, AppointmentStatus requested, String reason) {
        super("Cannot change appointment status from " + current + " to " + requested + ": " + reason);
    }
}
