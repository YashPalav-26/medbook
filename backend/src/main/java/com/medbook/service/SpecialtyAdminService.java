package com.medbook.service;

import com.medbook.dto.SpecialtyCreateRequest;
import com.medbook.dto.SpecialtyUpdateRequest;
import com.medbook.entity.Specialty;
import com.medbook.repository.SpecialtyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * ADMIN management of specialties (create / list-all / update / deactivate).
 *
 * <p>Specialties are reference data, so nothing is ever deleted — a retired
 * specialty is deactivated ({@code is_active = false}) and keeps its history:
 * existing appointments still point at it, and old records stay readable.
 * The public discovery API ({@link SpecialtyService}) keeps serving only active
 * rows; this service shows admins everything.</p>
 *
 * <p>Name uniqueness is enforced case-insensitively before the insert so the
 * caller gets a clean 409 instead of a database constraint violation (500).</p>
 */
@Service
public class SpecialtyAdminService {

    private final SpecialtyRepository specialtyRepository;

    public SpecialtyAdminService(SpecialtyRepository specialtyRepository) {
        this.specialtyRepository = specialtyRepository;
    }

    /**
     * Every specialty, active and inactive, alphabetically.
     */
    @Transactional(readOnly = true)
    public List<Specialty> listAll() {
        return specialtyRepository.findAllByOrderByNameAsc();
    }

    /**
     * Create a specialty.
     *
     * @throws DuplicateSpecialtyNameException when the name already exists (any casing)
     */
    @Transactional
    public Specialty create(SpecialtyCreateRequest request) {
        if (specialtyRepository.existsByNameIgnoreCase(request.name().trim())) {
            throw new DuplicateSpecialtyNameException(request.name().trim());
        }
        Specialty specialty = new Specialty(request.name().trim(), request.description());
        return specialtyRepository.save(specialty);
    }

    /**
     * Update name and description of an existing specialty.
     *
     * @throws SpecialtyNotFoundException      when the ID does not exist (404)
     * @throws DuplicateSpecialtyNameException when another specialty already uses the new name
     */
    @Transactional
    public Specialty update(Long id, SpecialtyUpdateRequest request) {
        Specialty specialty = requireSpecialty(id);
        String newName = request.name().trim();
        if (specialtyRepository.existsByNameIgnoreCaseAndIdNot(newName, id)) {
            throw new DuplicateSpecialtyNameException(newName);
        }
        specialty.setName(newName);
        specialty.setDescription(request.description());
        return specialtyRepository.save(specialty);
    }

    /**
     * Deactivate a specialty (soft state change — never a delete).
     *
     * @throws SpecialtyNotFoundException when the ID does not exist
     */
    @Transactional
    public Specialty deactivate(Long id) {
        Specialty specialty = requireSpecialty(id);
        specialty.setActive(false);
        return specialtyRepository.save(specialty);
    }

    private Specialty requireSpecialty(Long id) {
        return specialtyRepository.findById(id)
                .orElseThrow(() -> new SpecialtyNotFoundException(id));
    }
}
