package com.medbook.dto;

import java.time.LocalDate;
import java.util.Map;

/**
 * Real clinic overview for the ADMIN dashboard, from
 * {@code GET /api/v1/admin/appointments/overview}.
 *
 * <p>Every number is computed from live appointment rows — there are no estimated or
 * placeholder metrics. {@code totalAppointments} is simply the sum of the per-status
 * counts; statuses with zero appointments are reported as explicit {@code 0} so the
 * contract does not depend on which statuses happen to exist in the data.</p>
 *
 * @param todayAppointments how many appointments are booked for {@code today} (any status)
 * @param today             the date the {@code todayAppointments} figure refers to (ISO-8601),
 *                          so the client can label the metric without guessing the server's clock
 * @param statusCounts      appointments per lifecycle status (all five statuses always present)
 * @param totalAppointments all appointments ever booked, any status
 */
public record AdminOverviewResponse(
        long todayAppointments,
        LocalDate today,
        Map<String, Long> statusCounts,
        long totalAppointments
) {
}
