package com.medbook.dto;

import com.medbook.entity.DoctorAvailability;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * One availability window of a doctor ({@code GET /api/v1/doctors/{id}/availability}).
 *
 * <p>Exposes exactly the scheduling fields the booking page needs to guide time
 * selection; persistence details (foreign keys, creation timestamp) stay internal.
 * Day of week uses the project's MySQL convention: {@code 0 = Sunday ... 6 = Saturday}.</p>
 *
 * @param id             window identifier (used by the admin edit/close operations)
 * @param dayOfWeek      0 = Sunday ... 6 = Saturday
 * @param startTime      window start, serialized as {@code HH:mm}
 * @param endTime        window end, serialized as {@code HH:mm}
 * @param recurring      weekly-repeating window when {@code true}; one-off when {@code false}
 * @param effectiveFrom  first date the window applies (bounded recurring or one-off date; may be {@code null})
 * @param effectiveUntil last date the window applies (may be {@code null})
 */
public record DoctorAvailabilityResponse(
        Long id,
        int dayOfWeek,
        @com.fasterxml.jackson.annotation.JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @com.fasterxml.jackson.annotation.JsonFormat(pattern = "HH:mm") LocalTime endTime,
        boolean recurring,
        LocalDate effectiveFrom,
        LocalDate effectiveUntil
) {

    /** Map an availability row to its API representation. */
    public static DoctorAvailabilityResponse from(DoctorAvailability availability) {
        return new DoctorAvailabilityResponse(
                availability.getId(),
                availability.getDayOfWeek(),
                availability.getStartTime(),
                availability.getEndTime(),
                availability.isRecurring(),
                availability.getEffectiveFrom(),
                availability.getEffectiveUntil()
        );
    }
}
