package com.medbook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/v1/appointments/{id}/cancel}.
 *
 * <p>A cancellation reason is mandatory, exactly as the database requires it:
 * {@code CHECK (status != 'CANCELLED' OR cancellation_reason IS NOT NULL)}.
 * The 255 character limit mirrors the {@code cancellation_reason VARCHAR(255)} column.</p>
 *
 * <p>Whether the appointment may be cancelled at all (its current status) is not decided
 * here — that is a business rule owned by
 * {@link com.medbook.service.AppointmentService#cancelAppointment(Long, String)}.</p>
 *
 * @param cancellationReason why the appointment is cancelled (required, max 255 characters)
 */
public record CancelAppointmentRequest(

        @NotBlank(message = "cancellationReason is required")
        @Size(max = 255, message = "cancellationReason must be at most 255 characters")
        String cancellationReason
) {
}
