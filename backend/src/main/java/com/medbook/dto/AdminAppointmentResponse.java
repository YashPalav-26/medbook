package com.medbook.dto;

import com.medbook.entity.Appointment;
import com.medbook.entity.AppointmentStatus;
import com.medbook.entity.User;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * ADMIN-facing appointment row used by {@code GET /api/v1/admin/appointments} and the
 * admin cancellation response.
 *
 * <p>Unlike {@link AppointmentResponse} — which is the patient/doctor contract and
 * exposes participants as bare IDs — the admin view is support-oriented: it carries the
 * patient's and doctor's display names plus the doctor's specialty, so staff can identify
 * a visit at a glance without a second lookup per row.</p>
 *
 * <p><strong>Lazy-loading contract:</strong> {@link #from(Appointment)} reads
 * {@code patient.user} and {@code doctor.user/specialty}, which are lazy associations.
 * Call it only with appointments loaded through the repository queries that
 * {@code JOIN FETCH} those associations (the admin list/detail queries do), otherwise
 * an {@code LazyInitializationException} would be possible once OSIV is disabled — which
 * it is, by configuration.</p>
 *
 * <p>Still never exposed: password hashes, role assignments, audit columns the rest of
 * the appointment API also hides ({@code created_at}, {@code updated_at}).</p>
 *
 * @param id                      appointment identifier
 * @param patientId               ID of the patient who booked
 * @param patientName             "First Last" of the patient's account
 * @param doctorId                ID of the booked doctor
 * @param doctorName              "First Last" of the doctor's account
 * @param specialty               name of the doctor's specialty
 * @param appointmentDate         clinic visit date (ISO-8601)
 * @param startTime               window start, serialized as {@code HH:mm}
 * @param endTime                 window end, serialized as {@code HH:mm}
 * @param status                  current lifecycle status
 * @param cancellationReason      reason for cancellation, {@code null} unless cancelled
 * @param consultationFeeSnapshot fee captured when the appointment was created
 */
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

    /**
     * Map a fetch-joined appointment to its admin representation.
     *
     * @param appointment an appointment whose {@code patient.user}, {@code doctor.user}
     *                    and {@code doctor.specialty} associations are initialized
     * @return the admin response DTO for that appointment
     */
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

    /** "First Last" from the account — the display form used across the admin area. */
    private static String displayName(User user) {
        return user.getFirstName() + " " + user.getLastName();
    }
}
