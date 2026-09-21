package com.medbook.service;

import com.medbook.dto.AvailabilityCreateRequest;
import com.medbook.dto.AvailabilityUpdateRequest;
import com.medbook.entity.Doctor;
import com.medbook.entity.DoctorAvailability;
import com.medbook.repository.DoctorAvailabilityRepository;
import com.medbook.repository.DoctorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;

/**
 * ADMIN management of doctor availability windows
 * (create / list for a doctor / update / close).
 *
 * <p>Validation mirrors the {@code doctor_availability} schema and the booking-time
 * rules: weekday 0–6, {@code start < end}, {@code effectiveFrom <= effectiveUntil},
 * and the unique (doctor, day, start, end) constraint surfaced as a friendly 409
 * instead of a database error. Appointment conflict logic deliberately lives in
 * {@code AppointmentService}, not here — this table defines <em>when a doctor
 * could</em> work, not <em>who booked what</em>.</p>
 *
 * <p><strong>The schema has no {@code is_active} column on this table</strong> — so
 * there is no deactivate endpoint. The schema-native equivalent is <em>closing</em>
 * a window: setting {@code effective_until} to today (or a chosen date) stops it
 * applying to future dates while keeping the row's history. No migration needed.</p>
 */
@Service
public class AvailabilityAdminService {

    private final DoctorAvailabilityRepository availabilityRepository;
    private final DoctorRepository doctorRepository;

    public AvailabilityAdminService(DoctorAvailabilityRepository availabilityRepository,
                                    DoctorRepository doctorRepository) {
        this.availabilityRepository = availabilityRepository;
        this.doctorRepository = doctorRepository;
    }

    /**
     * Availability windows of one doctor (both open and closed), ordered for display.
     *
     * @throws DoctorNotFoundException when the doctor does not exist
     */
    @Transactional(readOnly = true)
    public List<DoctorAvailability> listForDoctor(Long doctorId) {
        requireDoctor(doctorId);
        return availabilityRepository.findByDoctorIdOrderByDayOfWeekAscStartTimeAsc(doctorId);
    }

    /**
     * Add an availability window to a doctor.
     *
     * @throws DoctorNotFoundException            when the doctor does not exist (404)
     * @throws IllegalArgumentException           when day/time/date-range values are invalid (400)
     * @throws DuplicateAvailabilitySlotException when the exact window already exists (409)
     */
    @Transactional
    public DoctorAvailability create(Long doctorId, AvailabilityCreateRequest request) {
        Doctor doctor = requireDoctor(doctorId);
        int dayOfWeek = validateDayOfWeek(request.dayOfWeek());
        validateWindow(request.startTime(), request.endTime());
        validateEffectiveRange(request.effectiveFrom(), request.effectiveUntil());

        if (availabilityRepository.existsByDoctorIdAndDayOfWeekAndStartTimeAndEndTime(
                doctorId, (byte) dayOfWeek, request.startTime(), request.endTime())) {
            throw new DuplicateAvailabilitySlotException();
        }

        DoctorAvailability availability = new DoctorAvailability(doctor, (byte) dayOfWeek,
                request.startTime(), request.endTime());
        availability.setRecurring(request.recurring() == null || request.recurring());
        availability.setEffectiveFrom(request.effectiveFrom());
        availability.setEffectiveUntil(request.effectiveUntil());
        return availabilityRepository.save(availability);
    }

    /**
     * Edit an availability window.
     *
     * @throws AvailabilityNotFoundException      when the window ID does not exist (404)
     * @throws IllegalArgumentException           when values are invalid (400)
     * @throws DuplicateAvailabilitySlotException when the exact window already exists on another row (409)
     */
    @Transactional
    public DoctorAvailability update(Long id, AvailabilityUpdateRequest request) {
        DoctorAvailability availability = requireAvailability(id);
        int dayOfWeek = validateDayOfWeek(request.dayOfWeek());
        validateWindow(request.startTime(), request.endTime());
        validateEffectiveRange(request.effectiveFrom(), request.effectiveUntil());

        if (availabilityRepository.existsByDoctorIdAndDayOfWeekAndStartTimeAndEndTimeAndIdNot(
                availability.getDoctor().getId(), (byte) dayOfWeek, request.startTime(), request.endTime(), id)) {
            throw new DuplicateAvailabilitySlotException();
        }

        availability.setDayOfWeek((byte) dayOfWeek);
        availability.setStartTime(request.startTime());
        availability.setEndTime(request.endTime());
        availability.setRecurring(request.recurring() == null || request.recurring());
        availability.setEffectiveFrom(request.effectiveFrom());
        availability.setEffectiveUntil(request.effectiveUntil());
        return availabilityRepository.save(availability);
    }

    /**
     * Close a window from today onwards — the schema-native "deactivation".
     *
     * <p>The {@code doctor_availability} table has no {@code is_active} column, so a
     * window is closed by clamping {@code effective_until} to yesterday: the row and
     * its history remain, but it no longer applies to any date from today forward.
     * Idempotent: closing an already-closed window succeeds without change.</p>
     *
     * <p>A window that starts in the future has never applied to anything, so closing
     * it removes the row outright — clamping {@code effective_until} below
     * {@code effective_from} would violate the schema's CHECK constraint. Availability
     * rows are configuration (nothing references them by FK), so this is safe.</p>
     *
     * @throws AvailabilityNotFoundException when the window ID does not exist
     */
    @Transactional
    public DoctorAvailability close(Long id) {
        DoctorAvailability availability = requireAvailability(id);
        java.time.LocalDate yesterday = java.time.LocalDate.now().minusDays(1);
        if (availability.getEffectiveFrom() != null && availability.getEffectiveFrom().isAfter(yesterday)) {
            availabilityRepository.delete(availability);
            return availability;
        }
        if (availability.getEffectiveUntil() == null || availability.getEffectiveUntil().isAfter(yesterday)) {
            availability.setEffectiveUntil(yesterday);
            return availabilityRepository.save(availability);
        }
        return availability;
    }

    private Doctor requireDoctor(Long doctorId) {
        return doctorRepository.findById(doctorId)
                .orElseThrow(() -> new DoctorNotFoundException(doctorId));
    }

    private DoctorAvailability requireAvailability(Long id) {
        return availabilityRepository.findById(id)
                .orElseThrow(() -> new AvailabilityNotFoundException(id));
    }

    private int validateDayOfWeek(Integer dayOfWeek) {
        if (dayOfWeek == null || dayOfWeek < 0 || dayOfWeek > 6) {
            throw new IllegalArgumentException("dayOfWeek must be between 0 (Sunday) and 6 (Saturday)");
        }
        return dayOfWeek;
    }

    private void validateWindow(LocalTime startTime, LocalTime endTime) {
        if (startTime == null || endTime == null || !startTime.isBefore(endTime)) {
            throw new IllegalArgumentException("Start time must be before end time");
        }
    }

    private void validateEffectiveRange(java.time.LocalDate effectiveFrom, java.time.LocalDate effectiveUntil) {
        if (effectiveFrom != null && effectiveUntil != null && effectiveUntil.isBefore(effectiveFrom)) {
            throw new IllegalArgumentException("effectiveUntil must not be before effectiveFrom");
        }
    }
}
