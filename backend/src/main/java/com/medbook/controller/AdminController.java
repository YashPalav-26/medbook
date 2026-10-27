package com.medbook.controller;

import com.medbook.dto.AdminAppointmentDetailResponse;
import com.medbook.dto.AdminAppointmentResponse;
import com.medbook.dto.AdminAccountResponse;
import com.medbook.dto.AdminCreateRequest;
import com.medbook.dto.AdminOverviewResponse;
import com.medbook.dto.AvailabilityCreateRequest;
import com.medbook.dto.AvailabilityUpdateRequest;
import com.medbook.dto.CancelAppointmentRequest;
import com.medbook.dto.DoctorAdminResponse;
import com.medbook.dto.DoctorCreateRequest;
import com.medbook.dto.DoctorUpdateRequest;
import com.medbook.dto.PatientAdminResponse;
import com.medbook.dto.SpecialtyCreateRequest;
import com.medbook.dto.SpecialtyResponse;
import com.medbook.dto.SpecialtyUpdateRequest;
import com.medbook.entity.AppointmentStatus;
import com.medbook.service.AdminAppointmentService;
import com.medbook.service.AdminAdminService;
import com.medbook.service.AvailabilityAdminService;
import com.medbook.service.DoctorAdminService;
import com.medbook.service.PatientAdminService;
import com.medbook.service.SpecialtyAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final SpecialtyAdminService specialtyAdminService;
    private final DoctorAdminService doctorAdminService;
    private final AvailabilityAdminService availabilityAdminService;
    private final AdminAppointmentService adminAppointmentService;
    private final PatientAdminService patientAdminService;
    private final AdminAdminService adminAdminService;

    public AdminController(SpecialtyAdminService specialtyAdminService,
                           DoctorAdminService doctorAdminService,
                           AvailabilityAdminService availabilityAdminService,
                           AdminAppointmentService adminAppointmentService,
                           PatientAdminService patientAdminService,
                           AdminAdminService adminAdminService) {
        this.specialtyAdminService = specialtyAdminService;
        this.doctorAdminService = doctorAdminService;
        this.availabilityAdminService = availabilityAdminService;
        this.adminAppointmentService = adminAppointmentService;
        this.patientAdminService = patientAdminService;
        this.adminAdminService = adminAdminService;
    }

    @GetMapping("/admins")
    public ResponseEntity<List<AdminAccountResponse>> listAdmins() {
        return ResponseEntity.ok(adminAdminService.listAll().stream()
                .map(AdminAccountResponse::from)
                .toList());
    }

    @PostMapping("/admins")
    public ResponseEntity<AdminAccountResponse> createAdmin(@Valid @RequestBody AdminCreateRequest request) {
        AdminAccountResponse response = AdminAccountResponse.from(adminAdminService.create(request));
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Location", "/api/v1/admin/admins/" + response.id())
                .body(response);
    }

    @GetMapping("/patients")
    public ResponseEntity<List<PatientAdminResponse>> listPatients() {
        return ResponseEntity.ok(patientAdminService.listAll().stream()
                .map(PatientAdminResponse::from)
                .toList());
    }

    @GetMapping("/specialties")
    public ResponseEntity<List<SpecialtyResponse>> listSpecialties() {
        return ResponseEntity.ok(specialtyAdminService.listAll().stream()
                .map(SpecialtyResponse::from)
                .toList());
    }

    @PostMapping("/specialties")
    public ResponseEntity<SpecialtyResponse> createSpecialty(@Valid @RequestBody SpecialtyCreateRequest request) {
        SpecialtyResponse response = SpecialtyResponse.from(specialtyAdminService.create(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/specialties/{id}")
    public ResponseEntity<SpecialtyResponse> updateSpecialty(
            @PathVariable Long id, @Valid @RequestBody SpecialtyUpdateRequest request) {
        return ResponseEntity.ok(SpecialtyResponse.from(specialtyAdminService.update(id, request)));
    }

    @PatchMapping("/specialties/{id}/deactivate")
    public ResponseEntity<SpecialtyResponse> deactivateSpecialty(@PathVariable Long id) {
        return ResponseEntity.ok(SpecialtyResponse.from(specialtyAdminService.deactivate(id)));
    }

    @GetMapping("/doctors")
    public ResponseEntity<List<DoctorAdminResponse>> listDoctors() {
        return ResponseEntity.ok(doctorAdminService.listAll().stream()
                .map(DoctorAdminResponse::from)
                .toList());
    }

    @GetMapping("/doctors/{id}")
    public ResponseEntity<DoctorAdminResponse> getDoctor(@PathVariable Long id) {
        return ResponseEntity.ok(DoctorAdminResponse.from(doctorAdminService.get(id)));
    }

    @PostMapping("/doctors")
    public ResponseEntity<DoctorAdminResponse> createDoctor(@Valid @RequestBody DoctorCreateRequest request) {
        DoctorAdminResponse response = DoctorAdminResponse.from(doctorAdminService.create(request));
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Location", "/api/v1/admin/doctors/" + response.id())
                .body(response);
    }

    @PutMapping("/doctors/{id}")
    public ResponseEntity<DoctorAdminResponse> updateDoctor(
            @PathVariable Long id, @Valid @RequestBody DoctorUpdateRequest request) {
        return ResponseEntity.ok(DoctorAdminResponse.from(doctorAdminService.update(id, request)));
    }

    @PatchMapping("/doctors/{id}/deactivate")
    public ResponseEntity<DoctorAdminResponse> deactivateDoctor(@PathVariable Long id) {
        return ResponseEntity.ok(DoctorAdminResponse.from(doctorAdminService.deactivate(id)));
    }

    @GetMapping("/doctors/{doctorId}/availability")
    public ResponseEntity<List<com.medbook.dto.DoctorAvailabilityResponse>> listAvailability(
            @PathVariable Long doctorId) {
        return ResponseEntity.ok(availabilityAdminService.listForDoctor(doctorId).stream()
                .map(com.medbook.dto.DoctorAvailabilityResponse::from)
                .toList());
    }

    @PostMapping("/doctors/{doctorId}/availability")
    public ResponseEntity<com.medbook.dto.DoctorAvailabilityResponse> createAvailability(
            @PathVariable Long doctorId, @Valid @RequestBody AvailabilityCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(com.medbook.dto.DoctorAvailabilityResponse.from(
                        availabilityAdminService.create(doctorId, request)));
    }

    @PutMapping("/availability/{id}")
    public ResponseEntity<com.medbook.dto.DoctorAvailabilityResponse> updateAvailability(
            @PathVariable Long id, @Valid @RequestBody AvailabilityUpdateRequest request) {
        return ResponseEntity.ok(com.medbook.dto.DoctorAvailabilityResponse.from(
                availabilityAdminService.update(id, request)));
    }

    @PatchMapping("/availability/{id}/deactivate")
    public ResponseEntity<com.medbook.dto.DoctorAvailabilityResponse> deactivateAvailability(@PathVariable Long id) {
        return ResponseEntity.ok(com.medbook.dto.DoctorAvailabilityResponse.from(
                availabilityAdminService.close(id)));
    }

    @GetMapping("/appointments")
    public ResponseEntity<List<AdminAppointmentResponse>> listAppointments(
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) Long patientId) {
        return ResponseEntity.ok(adminAppointmentService.listAppointments(date, status, doctorId, patientId));
    }

    @GetMapping("/appointments/overview")
    public ResponseEntity<AdminOverviewResponse> getOverview() {
        return ResponseEntity.ok(adminAppointmentService.getOverview());
    }

    @GetMapping("/appointments/{id}")
    public ResponseEntity<AdminAppointmentDetailResponse> getAppointmentDetail(@PathVariable Long id) {
        return ResponseEntity.ok(adminAppointmentService.getAppointmentDetail(id));
    }

    @PostMapping("/appointments/{id}/cancel")
    public ResponseEntity<AdminAppointmentResponse> cancelAppointment(
            @PathVariable Long id,
            @Valid @RequestBody CancelAppointmentRequest request) {
        AdminAppointmentResponse response = adminAppointmentService
                .cancelAppointmentAsAdmin(id, request.cancellationReason());
        return ResponseEntity.ok(response);
    }
}
