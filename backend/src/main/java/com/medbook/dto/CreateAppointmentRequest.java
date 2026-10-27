package com.medbook.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.time.LocalTime;

public record CreateAppointmentRequest(

        @NotNull(message = "patientId is required")
        @Positive(message = "patientId must be a positive number")
        Long patientId,

        @NotNull(message = "doctorId is required")
        @Positive(message = "doctorId must be a positive number")
        Long doctorId,

        @NotNull(message = "appointmentDate is required")
        @FutureOrPresent(message = "appointmentDate must be today or in the future")
        LocalDate appointmentDate,

        @NotNull(message = "startTime is required")
        LocalTime startTime,

        @NotNull(message = "endTime is required")
        LocalTime endTime
) {
}
