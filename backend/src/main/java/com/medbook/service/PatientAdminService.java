package com.medbook.service;

import com.medbook.entity.Patient;
import com.medbook.repository.PatientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * ADMIN read access to patients (Step: frontend coverage).
 *
 * <p>Deliberately read-only. Patients register themselves; the clinic's back office
 * needs to <em>see</em> who they are (booking on their behalf, phoning them back) —
 * creating or editing patient accounts through the admin API is not a feature this
 * project has chosen to implement, so this service offers no writes.</p>
 */
@Service
public class PatientAdminService {

    private final PatientRepository patientRepository;

    public PatientAdminService(PatientRepository patientRepository) {
        this.patientRepository = patientRepository;
    }

    /**
     * Every patient (active and inactive) with the linked login account fetched.
     */
    @Transactional(readOnly = true)
    public List<Patient> listAll() {
        return patientRepository.findAllWithUser();
    }
}
