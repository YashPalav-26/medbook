package com.medbook.service;

import com.medbook.dto.DoctorSummaryResponse;
import com.medbook.entity.Doctor;
import com.medbook.repository.DoctorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Read-only doctor discovery for the booking flow.
 *
 * <p>Behind {@code GET /api/v1/doctors}. This is deliberately a thin, listing-only
 * service: doctor profile management (create/update/deactivate) is administrative
 * work that does not exist yet and is out of scope. The only decision this service
 * makes is which doctors a patient may discover — <strong>active ones only</strong> —
 * so the booking UI can never offer a doctor who cannot accept appointments.</p>
 *
 * <p>Optionally filters by specialty (the booking page narrows the dropdown after the
 * patient picks one). Responses are DTOs ({@link DoctorSummaryResponse}); entities are
 * never exposed, so password hashes and login bookkeeping cannot leak.</p>
 */
@Service
public class DoctorService {

    private final DoctorRepository doctorRepository;

    public DoctorService(DoctorRepository doctorRepository) {
        this.doctorRepository = doctorRepository;
    }

    /**
     * List active doctors, optionally restricted to one specialty.
     *
     * <p>{@code readOnly} is sufficient: the fetch-join repository queries build the
     * full object graph in a single select and nothing is written.</p>
     *
     * @param specialtyId when non-null, only doctors of this specialty; when null, all specialties
     * @return active doctors mapped to booking-facing DTOs, ordered by id
     */
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
