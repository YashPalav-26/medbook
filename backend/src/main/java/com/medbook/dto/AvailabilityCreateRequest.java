package com.medbook.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Body of {@code POST /api/v1/admin/doctors/{doctorId}/availability} — add a scheduling window.
 *
 * <p>Fields mirror the {@code doctor_availability} schema exactly. Day of week uses
 * the project's MySQL convention: {@code 0 = Sunday ... 6 = Saturday}.</p>
 *
 * @param dayOfWeek      0 = Sunday ... 6 = Saturday (schema CHECK 0–6)
 * @param startTime      window start; the schema requires {@code start_time < end_time}
 * @param endTime        window end
 * @param recurring      weekly-repeating when true; a one-off window when false
 * @param effectiveFrom  first date the window applies (optional)
 * @param effectiveUntil last date the window applies (schema CHECK: not before effectiveFrom, optional)
 */
public record AvailabilityCreateRequest(

        @NotNull(message = "dayOfWeek is required")
        Integer dayOfWeek,

        @NotNull(message = "startTime is required")
        @JsonFormat(pattern = "HH:mm")
        LocalTime startTime,

        @NotNull(message = "endTime is required")
        @JsonFormat(pattern = "HH:mm")
        LocalTime endTime,

        Boolean recurring,

        LocalDate effectiveFrom,

        LocalDate effectiveUntil
) {
}
