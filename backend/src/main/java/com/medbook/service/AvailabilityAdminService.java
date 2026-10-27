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

@Service
public class AvailabilityAdminService {

    private final DoctorAvailabilityRepository availabilityRepository;
    private final DoctorRepository doctorRepository;

    public AvailabilityAdminService(DoctorAvailabilityRepository availabilityRepository,
                                    DoctorRepository doctorRepository) {
        this.availabilityRepository = availabilityRepository;
        this.doctorRepository = doctorRepository;
    }

    @Transactional(readOnly = true)
    public List<DoctorAvailability> listForDoctor(Long doctorId) {
        requireDoctor(doctorId);
        return availabilityRepository.findByDoctorIdOrderByDayOfWeekAscStartTimeAsc(doctorId);
    }

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
