package com.medbook.dto;

import com.medbook.entity.Appointment;
import com.medbook.entity.AppointmentStatus;
import com.medbook.entity.User;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Response body describing one appointment: used by
 * {@code POST /api/v1/appointments}, {@code GET /api/v1/appointments/{id}} and
 * {@code POST /api/v1/appointments/{id}/cancel}.
 *
 * <p><strong>Why a DTO and not the JPA entity?</strong> Returning {@link Appointment}
 * directly would couple the JSON contract to the database mapping (column names, lazy
 * associations, internal fields), and Jackson would try to walk {@code patient} and
 * {@code doctor}. The DTO is an explicit, stable contract: only these fields are ever
 * serialized, and the participants are exposed as IDs <em>plus</em> the safe display
 * names ("First Last") the UI needs to identify a visit without a second lookup.</p>
 *
 * <p>Only names are added — no address, insurance, medical or contact data. A patient
 * sees their own name and their doctor's name; a doctor sees the patient's name for an
 * appointment assigned to them. That is information both parties already need.</p>
 *
 * <p>The mutable, client-visible outcome of the booking — {@link #status} and
 * {@link #cancellationReason} — is produced by the service layer, never accepted from the
 * client. {@link #consultationFeeSnapshot} is included because the client must be able to
 * see the price that was locked in at booking time.</p>
 *
 * <p>The audit timestamps are database-generated ({@code created_at}) or set when a status
 * transition happens ({@code confirmed_at}, {@code completed_at}); they are read back from
 * the persisted row, so the same appointment always serializes identically.</p>
 *
 * <p>JSON shapes: dates use ISO-8601 ({@code "2026-10-01"}), times are pinned to
 * {@code "HH:mm"} by {@link JsonFormat} so the contract does not depend on Jackson defaults,
 * and {@code status} is the enum name (for example {@code "SCHEDULED"}).</p>
 *
 * <p><strong>Deliberately not exposed:</strong> {@code created_at}, {@code updated_at},
 * {@code confirmed_at} and {@code completed_at}. The first two are filled in by MySQL
 * ({@code DEFAULT CURRENT_TIMESTAMP(3)}) and the entity mapping marks them
 * {@code insertable = false, updatable = false}, so a just-inserted entity does not carry
 * them: including them would return {@code null} on {@code POST} but a real value on
 * {@code GET}. They will be added once the entity mapping is revisited (for example with
 * Hibernate's {@code @Generated}), together with the confirm/complete transitions that would
 * set the other two.</p>
 *
 * @param id                       appointment identifier
 * @param patientId                ID of the patient who booked
 * @param patientName              "First Last" of the patient's account
 * @param doctorId                 ID of the booked doctor
 * @param doctorName               "First Last" of the doctor's account
 * @param appointmentDate          clinic visit date
 * @param startTime                window start, serialized as {@code HH:mm}
 * @param endTime                  window end, serialized as {@code HH:mm}
 * @param status                   current status (SCHEDULED, CONFIRMED, COMPLETED, CANCELLED, NO_SHOW)
 * @param cancellationReason       reason for cancellation, {@code null} unless cancelled
 * @param consultationFeeSnapshot  consultation fee captured when the appointment was created
 */
public record AppointmentResponse(
        Long id,
        Long patientId,
        String patientName,
        Long doctorId,
        String doctorName,
        LocalDate appointmentDate,
        @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @JsonFormat(pattern = "HH:mm") LocalTime endTime,
        AppointmentStatus status,
        String cancellationReason,
        BigDecimal consultationFeeSnapshot
) {

    /**
     * Map a persisted appointment to its API representation.
     *
     * <p>Participant names are read from {@code patient.user} and {@code doctor.user},
     * which are lazy associations. Call this only with appointments whose participants and
     * their accounts were initialized (the service reloads with fetch joins before the
     * transaction ends), otherwise a {@code LazyInitializationException} is possible once
     * Open Session In View is disabled — which it is, by configuration.</p>
     *
     * @param appointment a persisted appointment (never {@code null})
     * @return the response DTO for that appointment
     */
    public static AppointmentResponse from(Appointment appointment) {
        return new AppointmentResponse(
                appointment.getId(),
                appointment.getPatient().getId(),
                displayName(appointment.getPatient().getUser()),
                appointment.getDoctor().getId(),
                displayName(appointment.getDoctor().getUser()),
                appointment.getAppointmentDate(),
                appointment.getStartTime(),
                appointment.getEndTime(),
                appointment.getStatus(),
                appointment.getCancellationReason(),
                appointment.getConsultationFeeSnapshot()
        );
    }

    /** "First Last" from the account — the display form used across the appointment UI. */
    private static String displayName(User user) {
        return user.getFirstName() + " " + user.getLastName();
    }
}
