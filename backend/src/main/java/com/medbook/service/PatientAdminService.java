package com.medbook.service;

import com.medbook.entity.Patient;
import com.medbook.repository.PatientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;









@Service
public class PatientAdminService {

    private final PatientRepository patientRepository;

    public PatientAdminService(PatientRepository patientRepository) {
        this.patientRepository = patientRepository;
    }




    @Transactional(readOnly = true)
    public List<Patient> listAll() {
        return patientRepository.findAllWithUser();
    }
}
