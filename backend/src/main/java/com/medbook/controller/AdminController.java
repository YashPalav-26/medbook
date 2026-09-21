package com.medbook.controller;

import com.medbook.dto.AvailabilityCreateRequest;
import com.medbook.dto.AvailabilityUpdateRequest;
import com.medbook.dto.DoctorAdminResponse;
import com.medbook.dto.DoctorCreateRequest;
import com.medbook.dto.DoctorUpdateRequest;
import com.medbook.dto.SpecialtyCreateRequest;
import com.medbook.dto.SpecialtyResponse;
import com.medbook.dto.SpecialtyUpdateRequest;
import com.medbook.service.AvailabilityAdminService;
import com.medbook.service.DoctorAdminService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * ADMIN management REST API — specialties, doctors and their availability.
 *
 * <p>Every route requires the ADMIN role (enforced by the security configuration's
 * {@code /api/v1/admin/** → hasRole(ADMIN)} matcher, backed by real JWT roles).
 * Anonymous callers get 401 from the authentication entry point; authenticated
 * non-admins get 403 from {@code MedbookAccessDeniedHandler}. Controllers stay
 * thin: all decisions live in the admin services.</p>
 *
 * <p>Responses are DTOs only — no JPA entity, and therefore no password hash or
 * other security internal, can ever be serialized here.</p>
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final SpecialtyAdminService specialtyAdminService;
    private final DoctorAdminService doctorAdminService;
    private final AvailabilityAdminService availabilityAdminService;

    public AdminController(SpecialtyAdminService specialtyAdminService,
                           DoctorAdminService doctorAdminService,
                           AvailabilityAdminService availabilityAdminService) {
        this.specialtyAdminService = specialtyAdminService;
        this.doctorAdminService = doctorAdminService;
        this.availabilityAdminService = availabilityAdminService;
    }

    // ------------------------------------------------------------------
    // Specialties
    // ------------------------------------------------------------------

    /** List all specialties (active and inactive). */
    @GetMapping("/specialties")
    public ResponseEntity<List<SpecialtyResponse>> listSpecialties() {
        return ResponseEntity.ok(specialtyAdminService.listAll().stream()
                .map(SpecialtyResponse::from)
                .toList());
    }

    /** Create a specialty. 409 when the name already exists. */
    @PostMapping("/specialties")
    public ResponseEntity<SpecialtyResponse> createSpecialty(@Valid @RequestBody SpecialtyCreateRequest request) {
        SpecialtyResponse response = SpecialtyResponse.from(specialtyAdminService.create(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** Update a specialty's name/description. 404 unknown, 409 duplicate name. */
    @PutMapping("/specialties/{id}")
    public ResponseEntity<SpecialtyResponse> updateSpecialty(
            @PathVariable Long id, @Valid @RequestBody SpecialtyUpdateRequest request) {
        return ResponseEntity.ok(SpecialtyResponse.from(specialtyAdminService.update(id, request)));
    }

    /** Deactivate a specialty (soft state change, never a delete). */
    @PatchMapping("/specialties/{id}/deactivate")
    public ResponseEntity<SpecialtyResponse> deactivateSpecialty(@PathVariable Long id) {
        return ResponseEntity.ok(SpecialtyResponse.from(specialtyAdminService.deactivate(id)));
    }

    // ------------------------------------------------------------------
    // Doctors
    // ------------------------------------------------------------------

    /** List all doctors (active and inactive). */
    @GetMapping("/doctors")
    public ResponseEntity<List<DoctorAdminResponse>> listDoctors() {
        return ResponseEntity.ok(doctorAdminService.listAll().stream()
                .map(DoctorAdminResponse::from)
                .toList());
    }

    /** One doctor by ID. */
    @GetMapping("/doctors/{id}")
    public ResponseEntity<DoctorAdminResponse> getDoctor(@PathVariable Long id) {
        return ResponseEntity.ok(DoctorAdminResponse.from(doctorAdminService.get(id)));
    }

    /**
     * Create a doctor with their login account and DOCTOR role — one transaction.
     * 409 duplicate email/license, 404 unknown specialty.
     */
    @PostMapping("/doctors")
    public ResponseEntity<DoctorAdminResponse> createDoctor(@Valid @RequestBody DoctorCreateRequest request) {
        DoctorAdminResponse response = DoctorAdminResponse.from(doctorAdminService.create(request));
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Location", "/api/v1/admin/doctors/" + response.id())
                .body(response);
    }

    /** Update a doctor's profile. 404 unknown, 409 duplicate license. */
    @PutMapping("/doctors/{id}")
    public ResponseEntity<DoctorAdminResponse> updateDoctor(
            @PathVariable Long id, @Valid @RequestBody DoctorUpdateRequest request) {
        return ResponseEntity.ok(DoctorAdminResponse.from(doctorAdminService.update(id, request)));
    }

    /** Deactivate a doctor's profile (discovery stops offering them; nothing is deleted). */
    @PatchMapping("/doctors/{id}/deactivate")
    public ResponseEntity<DoctorAdminResponse> deactivateDoctor(@PathVariable Long id) {
        return ResponseEntity.ok(DoctorAdminResponse.from(doctorAdminService.deactivate(id)));
    }

    // ------------------------------------------------------------------
    // Availability
    // ------------------------------------------------------------------

    /** List a doctor's availability windows (open and closed). */
    @GetMapping("/doctors/{doctorId}/availability")
    public ResponseEntity<List<com.medbook.dto.DoctorAvailabilityResponse>> listAvailability(
            @PathVariable Long doctorId) {
        return ResponseEntity.ok(availabilityAdminService.listForDoctor(doctorId).stream()
                .map(com.medbook.dto.DoctorAvailabilityResponse::from)
                .toList());
    }

    /** Add an availability window. 404 unknown doctor, 400 invalid values, 409 duplicate. */
    @PostMapping("/doctors/{doctorId}/availability")
    public ResponseEntity<com.medbook.dto.DoctorAvailabilityResponse> createAvailability(
            @PathVariable Long doctorId, @Valid @RequestBody AvailabilityCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(com.medbook.dto.DoctorAvailabilityResponse.from(
                        availabilityAdminService.create(doctorId, request)));
    }

    /** Edit an availability window. */
    @PutMapping("/availability/{id}")
    public ResponseEntity<com.medbook.dto.DoctorAvailabilityResponse> updateAvailability(
            @PathVariable Long id, @Valid @RequestBody AvailabilityUpdateRequest request) {
        return ResponseEntity.ok(com.medbook.dto.DoctorAvailabilityResponse.from(
                availabilityAdminService.update(id, request)));
    }

    /**
     * Close a window (the schema has no {@code is_active} column, so "deactivate"
     * means clamping {@code effective_until} — see {@link AvailabilityAdminService#close}).
     */
    @PatchMapping("/availability/{id}/deactivate")
    public ResponseEntity<com.medbook.dto.DoctorAvailabilityResponse> deactivateAvailability(@PathVariable Long id) {
        return ResponseEntity.ok(com.medbook.dto.DoctorAvailabilityResponse.from(
                availabilityAdminService.close(id)));
    }
}
