package com.medbook.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

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
