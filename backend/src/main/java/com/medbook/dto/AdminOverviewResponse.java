package com.medbook.dto;

import java.time.LocalDate;
import java.util.Map;
















public record AdminOverviewResponse(
        long todayAppointments,
        LocalDate today,
        Map<String, Long> statusCounts,
        long totalAppointments
) {
}
