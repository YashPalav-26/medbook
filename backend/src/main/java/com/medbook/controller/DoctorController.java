package com.medbook.controller;

import com.medbook.dto.DoctorAvailabilityResponse;
import com.medbook.dto.DoctorSummaryResponse;
import com.medbook.service.AvailabilityService;
import com.medbook.service.DoctorService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;













@RestController
@RequestMapping("/api/v1")
public class DoctorController {

    private final DoctorService doctorService;
    private final AvailabilityService availabilityService;

    public DoctorController(DoctorService doctorService, AvailabilityService availabilityService) {
        this.doctorService = doctorService;
        this.availabilityService = availabilityService;
    }











    @GetMapping("/doctors")
    public ResponseEntity<List<DoctorSummaryResponse>> listDoctors(
            @RequestParam(name = "specialtyId", required = false) Long specialtyId) {
        return ResponseEntity.ok(doctorService.listActiveDoctors(specialtyId));
    }
















    @GetMapping("/doctors/{doctorId}/availability")
    public ResponseEntity<List<DoctorAvailabilityResponse>> getAvailability(
            @PathVariable Long doctorId,
            @RequestParam(name = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(availabilityService.getDoctorAvailability(doctorId, date));
    }
}
