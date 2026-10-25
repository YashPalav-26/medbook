package com.medbook.dto;

import com.medbook.entity.DoctorAvailability;

import java.time.LocalDate;
import java.time.LocalTime;
















public record DoctorAvailabilityResponse(
        Long id,
        int dayOfWeek,
        @com.fasterxml.jackson.annotation.JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @com.fasterxml.jackson.annotation.JsonFormat(pattern = "HH:mm") LocalTime endTime,
        boolean recurring,
        LocalDate effectiveFrom,
        LocalDate effectiveUntil
) {


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
