package com.medbook.service;

import com.medbook.dto.DoctorAvailabilityResponse;
import com.medbook.entity.DoctorAvailability;
import com.medbook.repository.DoctorAvailabilityRepository;
import com.medbook.repository.DoctorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Doctor availability lookup for the booking flow.
 *
 * <p>Behind {@code GET /api/v1/doctors/{doctorId}/availability}. Returns the doctor's
 * windows ({@link DoctorAvailabilityResponse}) so the booking page can show which
 * weekday/hours are possible <em>before</em> a submission is attempted. The optional
 * {@code date} parameter pre-filters to windows actually in force on that day —
 * convenience for the UI, not a security or correctness feature.</p>
 *
 * <p><strong>The backend remains authoritative:</strong> the booking-time validation in
 * {@code AppointmentService} re-checks availability, weekday, effective dates and
 * conflicts against the database at submit time. Availability shown in the UI is
 * guidance, never proof. No second scheduling model is introduced — the same
 * {@code doctor_availability} rows feed both this API and the validation.</p>
 */
@Service
public class AvailabilityService {

    private final DoctorRepository doctorRepository;
    private final DoctorAvailabilityRepository availabilityRepository;

    public AvailabilityService(DoctorRepository doctorRepository,
                               DoctorAvailabilityRepository availabilityRepository) {
        this.doctorRepository = doctorRepository;
        this.availabilityRepository = availabilityRepository;
    }

    /**
     * Availability windows of one doctor, optionally restricted to a specific date.
     *
     * @param doctorId doctor whose schedule to read
     * @param date     when non-null, only windows in force on that date (weekday, effective range);
     *                 when null, every window of the doctor
     * @return availability DTOs ordered by day of week, then start time
     * @throws DoctorNotFoundException if the doctor does not exist (HTTP 404)
     */
    @Transactional(readOnly = true)
    public List<DoctorAvailabilityResponse> getDoctorAvailability(Long doctorId, LocalDate date) {
        requireExistingDoctor(doctorId);
        List<DoctorAvailability> windows = availabilityRepository.findByDoctorIdOrderByDayOfWeekAscStartTimeAsc(doctorId);
        if (date == null) {
            return windows.stream().map(DoctorAvailabilityResponse::from).toList();
        }
        byte dayOfWeek = (byte) (date.getDayOfWeek().getValue() % 7); // Mon=1..Sun=7 → Sun=0..Sat=6
        return windows.stream()
                .filter(w -> w.getDayOfWeek() == dayOfWeek)
                .filter(w -> isEffectiveOn(w, date))
                .map(DoctorAvailabilityResponse::from)
                .toList();
    }

    /**
     * Availability windows of one doctor for a specific date, plus the resolved date.
     *
     * <p>Convenience overload when the caller already knows the date (the booking page
     * always does) — saves the client a weekday recomputation.</p>
     */
    @Transactional(readOnly = true)
    public List<DoctorAvailabilityResponse> getDoctorAvailabilityForDate(Long doctorId, LocalDate date) {
        return getDoctorAvailability(doctorId, date);
    }

    private void requireExistingDoctor(Long doctorId) {
        doctorRepository.findById(doctorId)
                .orElseThrow(() -> new DoctorNotFoundException(doctorId));
    }

    /**
     * Whether a window is in force on {@code date}, mirroring the booking-time rule:
     * recurring windows repeat weekly (bounded by the effective range when set);
     * non-recurring windows apply to their single effective day.
     */
    private boolean isEffectiveOn(DoctorAvailability window, LocalDate date) {
        LocalDate from = window.getEffectiveFrom();
        LocalDate until = window.getEffectiveUntil();
        if (from != null && date.isBefore(from)) {
            return false;
        }
        if (until != null && date.isAfter(until)) {
            return false;
        }
        if (!window.isRecurring()) {
            // One-off window: applies to a single day. With no effectiveFrom there is
            // no way to anchor it to a date, so it never matches a concrete day.
            return from != null && date.isEqual(from);
        }
        return true;
    }
}
