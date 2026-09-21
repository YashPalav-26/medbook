package com.medbook.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Body of {@code PUT /api/v1/admin/availability/{id}} — edit a scheduling window.
 *
 * @param dayOfWeek      0 = Sunday ... 6 = Saturday
 * @param startTime      window start (must be before endTime)
 * @param endTime        window end
 * @param recurring      weekly-repeating when true
 * @param effectiveFrom  first date the window applies (optional)
 * @param effectiveUntil last date the window applies (not before effectiveFrom, optional)
 */
public record AvailabilityUpdateRequest(

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
