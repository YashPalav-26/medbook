package com.medbook.service;

import com.medbook.dto.DoctorAvailabilityResponse;
import com.medbook.entity.DoctorAvailability;
import com.medbook.repository.DoctorAvailabilityRepository;
import com.medbook.repository.DoctorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
















@Service
public class AvailabilityService {

    private final DoctorRepository doctorRepository;
    private final DoctorAvailabilityRepository availabilityRepository;

    public AvailabilityService(DoctorRepository doctorRepository,
                               DoctorAvailabilityRepository availabilityRepository) {
        this.doctorRepository = doctorRepository;
        this.availabilityRepository = availabilityRepository;
    }










    @Transactional(readOnly = true)
    public List<DoctorAvailabilityResponse> getDoctorAvailability(Long doctorId, LocalDate date) {
        requireExistingDoctor(doctorId);
        List<DoctorAvailability> windows = availabilityRepository.findByDoctorIdOrderByDayOfWeekAscStartTimeAsc(doctorId);
        if (date == null) {
            return windows.stream().map(DoctorAvailabilityResponse::from).toList();
        }
        byte dayOfWeek = (byte) (date.getDayOfWeek().getValue() % 7);
        return windows.stream()
                .filter(w -> w.getDayOfWeek() == dayOfWeek)
                .filter(w -> isEffectiveOn(w, date))
                .map(DoctorAvailabilityResponse::from)
                .toList();
    }







    @Transactional(readOnly = true)
    public List<DoctorAvailabilityResponse> getDoctorAvailabilityForDate(Long doctorId, LocalDate date) {
        return getDoctorAvailability(doctorId, date);
    }

    private void requireExistingDoctor(Long doctorId) {
        doctorRepository.findById(doctorId)
                .orElseThrow(() -> new DoctorNotFoundException(doctorId));
    }






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


            return from != null && date.isEqual(from);
        }
        return true;
    }
}
