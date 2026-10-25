package com.medbook.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;




















public record PatientBookingRequest(

        @NotNull(message = "doctorId is required")
        @Positive(message = "doctorId must be a positive number")
        Long doctorId,

        @NotNull(message = "appointmentDate is required")
        @FutureOrPresent(message = "appointmentDate must be today or in the future")
        LocalDate appointmentDate,

        @NotNull(message = "startTime is required")
        LocalTime startTime,

        @NotNull(message = "endTime is required")
        LocalTime endTime,

        @Size(max = 500, message = "reason must be at most 500 characters")
        String reason
) {
}
