package com.medbook.controller;

import com.medbook.dto.SpecialtyResponse;
import com.medbook.service.SpecialtyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Specialty discovery REST API (read-only).
 *
 * <p>Behind {@code GET /api/v1/specialties}. Serves the booking UI's specialty
 * dropdown — active specialties only, alphabetically. There is no specialty CRUD:
 * specialties are reference data managed administratively, and no admin tooling
 * exists in this step.</p>
 *
 * <p>Requires authentication (covered by {@code anyRequest().authenticated()});
 * the response uses the {@link SpecialtyResponse} DTO, never the entity.</p>
 */
@RestController
@RequestMapping("/api/v1")
public class SpecialtyController {

    private final SpecialtyService specialtyService;

    public SpecialtyController(SpecialtyService specialtyService) {
        this.specialtyService = specialtyService;
    }

    /**
     * List active specialties.
     *
     * @return active specialties ordered by name
     */
    @GetMapping("/specialties")
    public ResponseEntity<List<SpecialtyResponse>> listSpecialties() {
        return ResponseEntity.ok(specialtyService.listActiveSpecialties());
    }
}
