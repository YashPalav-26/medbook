package com.medbook.service;

import com.medbook.dto.SpecialtyResponse;
import com.medbook.repository.SpecialtyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Read-only specialty discovery for the booking flow.
 *
 * <p>Behind {@code GET /api/v1/specialties}. Specialties are reference data managed
 * administratively — there is no CRUD here (and none is planned for this step).
 * The service simply decides <em>which</em> specialties a patient may see:
 * active ones only, alphabetically, so the booking dropdown never offers a
 * retired specialty.</p>
 */
@Service
public class SpecialtyService {

    private final SpecialtyRepository specialtyRepository;

    public SpecialtyService(SpecialtyRepository specialtyRepository) {
        this.specialtyRepository = specialtyRepository;
    }

    /**
     * All active specialties, alphabetically.
     *
     * @return active specialties mapped to {@link SpecialtyResponse}
     */
    @Transactional(readOnly = true)
    public List<SpecialtyResponse> listActiveSpecialties() {
        return specialtyRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(SpecialtyResponse::from)
                .toList();
    }
}
