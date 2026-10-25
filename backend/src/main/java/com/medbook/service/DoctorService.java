package com.medbook.service;

import com.medbook.dto.DoctorSummaryResponse;
import com.medbook.entity.Doctor;
import com.medbook.repository.DoctorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;














@Service
public class DoctorService {

    private final DoctorRepository doctorRepository;

    public DoctorService(DoctorRepository doctorRepository) {
        this.doctorRepository = doctorRepository;
    }










    @Transactional(readOnly = true)
    public List<DoctorSummaryResponse> listActiveDoctors(Long specialtyId) {
        List<Doctor> doctors = (specialtyId == null)
                ? doctorRepository.findAllByActiveTrueWithUserAndSpecialty()
                : doctorRepository.findAllBySpecialtyIdAndActiveTrueWithUserAndSpecialty(specialtyId);
        return doctors.stream()
                .map(DoctorSummaryResponse::from)
                .toList();
    }
}
