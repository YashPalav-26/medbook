package com.medbook.service;

import com.medbook.dto.SpecialtyCreateRequest;
import com.medbook.dto.SpecialtyUpdateRequest;
import com.medbook.entity.Specialty;
import com.medbook.repository.SpecialtyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;













@Service
public class SpecialtyAdminService {

    private final SpecialtyRepository specialtyRepository;

    public SpecialtyAdminService(SpecialtyRepository specialtyRepository) {
        this.specialtyRepository = specialtyRepository;
    }




    @Transactional(readOnly = true)
    public List<Specialty> listAll() {
        return specialtyRepository.findAllByOrderByNameAsc();
    }






    @Transactional
    public Specialty create(SpecialtyCreateRequest request) {
        if (specialtyRepository.existsByNameIgnoreCase(request.name().trim())) {
            throw new DuplicateSpecialtyNameException(request.name().trim());
        }
        Specialty specialty = new Specialty(request.name().trim(), request.description());
        return specialtyRepository.save(specialty);
    }







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
