package com.medbook.controller;

import com.medbook.dto.DoctorAvailabilityResponse;
import com.medbook.dto.DoctorSummaryResponse;
import com.medbook.service.AvailabilityService;
import com.medbook.service.DoctorService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Doctor discovery REST API (read-only).
 *
 * <p>Booking-facing reads only: listing active doctors and their availability.
 * Doctor profile management is administrative work that does not exist yet and
 * is deliberately not started here. Both routes require authentication (they fall
 * under the security configuration's {@code anyRequest().authenticated()}) — an
 * unauthenticated caller gets 401 before any business logic runs.</p>
 *
 * <p>Responses are DTOs; the JPA entities (and through them the linked
 * {@code users} rows with password hashes) are never serialized.</p>
 */
@RestController
@RequestMapping("/api/v1")
public class DoctorController {

    private final DoctorService doctorService;
    private final AvailabilityService availabilityService;

    public DoctorController(DoctorService doctorService, AvailabilityService availabilityService) {
        this.doctorService = doctorService;
        this.availabilityService = availabilityService;
    }

    /**
     * List active doctors, optionally filtered by specialty.
     *
     * <p>{@code GET /api/v1/doctors} — all active doctors.
     * {@code GET /api/v1/doctors?specialtyId=5} — active doctors of one specialty
     * (the booking page narrows the doctor dropdown after a specialty is chosen).</p>
     *
     * @param specialtyId optional specialty filter
     * @return active doctors, ordered by id
     */
    @GetMapping("/doctors")
    public ResponseEntity<List<DoctorSummaryResponse>> listDoctors(
            @RequestParam(name = "specialtyId", required = false) Long specialtyId) {
        return ResponseEntity.ok(doctorService.listActiveDoctors(specialtyId));
    }

    /**
     * Availability windows of one doctor.
     *
     * <p>{@code GET /api/v1/doctors/{doctorId}/availability} — the doctor's full
     * weekly schedule. {@code GET /api/v1/doctors/{doctorId}/availability?date=2026-10-05}
     * — only the windows actually in force on that date (weekday + effective range),
     * which is what the booking page asks for once a date is picked.</p>
     *
     * <p>This is guidance for the UI; the booking endpoint re-validates everything
     * server-side at submit time.</p>
     *
     * @param doctorId doctor whose schedule to read
     * @param date     optional date filter ({@code yyyy-MM-dd})
     * @return availability windows, ordered by day of week then start time
     */
    @GetMapping("/doctors/{doctorId}/availability")
    public ResponseEntity<List<DoctorAvailabilityResponse>> getAvailability(
            @PathVariable Long doctorId,
            @RequestParam(name = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(availabilityService.getDoctorAvailability(doctorId, date));
    }
}
