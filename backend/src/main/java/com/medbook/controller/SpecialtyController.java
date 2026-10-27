package com.medbook.controller;

import com.medbook.dto.SpecialtyResponse;
import com.medbook.service.SpecialtyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class SpecialtyController {

    private final SpecialtyService specialtyService;

    public SpecialtyController(SpecialtyService specialtyService) {
        this.specialtyService = specialtyService;
    }

    @GetMapping("/specialties")
    public ResponseEntity<List<SpecialtyResponse>> listSpecialties() {
        return ResponseEntity.ok(specialtyService.listActiveSpecialties());
    }
}
