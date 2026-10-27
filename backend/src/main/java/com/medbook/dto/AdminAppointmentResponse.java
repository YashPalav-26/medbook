package com.medbook.dto;

import com.medbook.entity.Appointment;
import com.medbook.entity.AppointmentStatus;
import com.medbook.entity.User;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public record AdminAppointmentResponse(
        Long id,
        Long patientId,
        String patientName,
        Long doctorId,
        String doctorName,
        String specialty,
        LocalDate appointmentDate,
        @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @JsonFormat(pattern = "HH:mm") LocalTime endTime,
        AppointmentStatus status,
        String cancellationReason,
        BigDecimal consultationFeeSnapshot
) {

    public static AdminAppointmentResponse from(Appointment appointment) {
        return new AdminAppointmentResponse(
                appointment.getId(),
                appointment.getPatient().getId(),
                displayName(appointment.getPatient().getUser()),
                appointment.getDoctor().getId(),
                displayName(appointment.getDoctor().getUser()),
                appointment.getDoctor().getSpecialty().getName(),
                appointment.getAppointmentDate(),
                appointment.getStartTime(),
                appointment.getEndTime(),
                appointment.getStatus(),
                appointment.getCancellationReason(),
                appointment.getConsultationFeeSnapshot()
        );
    }

    private static String displayName(User user) {
        return user.getFirstName() + " " + user.getLastName();
    }
}
