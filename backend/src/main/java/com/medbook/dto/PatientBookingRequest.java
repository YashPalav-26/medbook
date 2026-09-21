package com.medbook.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Body of {@code POST /api/v1/appointments} for a PATIENT caller — a self-booking.
 *
 * <p><strong>Deliberately no {@code patientId}.</strong> The booking patient is resolved
 * from the authenticated caller (JWT {@code sub} → user → patient profile) inside
 * {@code AppointmentService}. A patient who puts someone else's ID on the form changes
 * nothing: the value is not even read for patient callers, and the caller identity can
 * never be fabricated from request data.</p>
 *
 * <p>ADMIN callers use {@link CreateAppointmentRequest} (with {@code patientId}) for the
 * support path — booking on behalf of a patient who cannot use the app. That capability
 * is justified in {@code docs/doctor-discovery-and-booking.md}.</p>
 *
 * @param reason          optional visit reason shown to the clinic (max 500 chars)
 * @param doctorId        ID of the doctor being booked
 * @param appointmentDate the clinic visit date, today or later
 * @param startTime       window start, for example {@code "10:00"}
 * @param endTime         window end, which must be after {@code startTime}
 */
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
