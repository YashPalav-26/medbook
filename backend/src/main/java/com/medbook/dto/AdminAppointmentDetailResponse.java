package com.medbook.dto;

import com.medbook.entity.Appointment;

import java.util.List;

/**
 * ADMIN appointment detail: the full appointment view plus the status-change audit
 * trail, in one response for {@code GET /api/v1/admin/appointments/{id}}.
 *
 * <p>Everything the admin UI needs on the detail screen comes from here — no second
 * endpoint and no stitching. The wrapped {@link AdminAppointmentResponse} already carries
 * patient/doctor names and the specialty; {@code history} adds the lifecycle timeline
 * (status, note, actor name, time) which the patient/doctor-facing
 * {@link AppointmentResponse} deliberately does not expose.</p>
 *
 * @param appointment the appointment itself (names, specialty, fee, cancellation info)
 * @param history     the audit trail, oldest first — append-only, never rewritten
 */
public record AdminAppointmentDetailResponse(
        AdminAppointmentResponse appointment,
        List<AppointmentStatusHistoryResponse> history
) {

    /**
     * Assemble the detail response from a fetch-joined appointment and its history rows.
     *
     * @param appointment appointment loaded with all participants initialized
     *                    ({@link com.medbook.repository.AppointmentRepository#findByIdForAdmin})
     * @param history     that appointment's history rows, oldest first
     * @return the admin detail DTO
     */
    public static AdminAppointmentDetailResponse from(Appointment appointment,
                                                      List<AppointmentStatusHistoryResponse> history) {
        return new AdminAppointmentDetailResponse(
                AdminAppointmentResponse.from(appointment),
                List.copyOf(history)
        );
    }
}
