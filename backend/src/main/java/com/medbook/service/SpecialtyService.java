package com.medbook.service;

import com.medbook.dto.SpecialtyResponse;
import com.medbook.repository.SpecialtyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;










@Service
public class SpecialtyService {

    private final SpecialtyRepository specialtyRepository;

    public SpecialtyService(SpecialtyRepository specialtyRepository) {
        this.specialtyRepository = specialtyRepository;
    }






    @Transactional(readOnly = true)
    public List<SpecialtyResponse> listActiveSpecialties() {
        return specialtyRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(SpecialtyResponse::from)
                .toList();
    }
}
