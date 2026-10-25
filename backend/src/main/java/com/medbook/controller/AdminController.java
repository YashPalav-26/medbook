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

    // ------------------------------------------------------------------
    // Administrators (admin account management)
    // ------------------------------------------------------------------

    /**
     * List every administrator account (active and inactive) with role names.
     * Passwords — hashed or otherwise — are never part of the response.
     */
    @GetMapping("/admins")
    public ResponseEntity<List<AdminAccountResponse>> listAdmins() {
        return ResponseEntity.ok(adminAdminService.listAll().stream()
                .map(AdminAccountResponse::from)
                .toList());
    }

    /**
     * Create an administrator with their login account and ADMIN role — one
     * transaction. The role is assigned server-side; the request carries no role
     * field. 409 duplicate email.
     */
    @PostMapping("/admins")
    public ResponseEntity<AdminAccountResponse> createAdmin(@Valid @RequestBody AdminCreateRequest request) {
        AdminAccountResponse response = AdminAccountResponse.from(adminAdminService.create(request));
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Location", "/api/v1/admin/admins/" + response.id())
                .body(response);
    }

    // ------------------------------------------------------------------
    // Patients (read-only: patients self-register; admins book on their behalf)
    // ------------------------------------------------------------------

    /** List all patients (active and inactive) with their account linkage. */
    @GetMapping("/patients")
    public ResponseEntity<List<PatientAdminResponse>> listPatients() {
        return ResponseEntity.ok(patientAdminService.listAll().stream()
                .map(PatientAdminResponse::from)
                .toList());
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

    // ------------------------------------------------------------------
    // Appointments (clinic-wide visibility + support cancellation)
    // ------------------------------------------------------------------

    /**
     * List appointments across the whole clinic, with every filter optional.
     *
     * <p>All parameters may be combined: {@code ?date=2026-09-28}, {@code ?status=CONFIRMED},
     * {@code ?doctorId=1}, {@code ?patientId=2}, or any mix. Omitted parameters are simply
     * not filtered on. This is the clinic-wide counterpart of
     * {@code /api/v1/appointments/me}, which serves each patient's and doctor's own
     * appointments; admins do not have "own" appointments, so they were rejected there
     * (403) and read here instead.</p>
     *
     * <p>Wrong enum or date values fail request conversion with 400 before any query runs.
     */
    @GetMapping("/appointments")
    public ResponseEntity<List<AdminAppointmentResponse>> listAppointments(
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) Long patientId) {
        return ResponseEntity.ok(adminAppointmentService.listAppointments(date, status, doctorId, patientId));
    }

    /**
     * Real clinic overview for the dashboard: today's appointment count and the
     * per-status breakdown, computed from live rows (no estimated metrics).
     * Declared before {@code /appointments/{id}} so "overview" is never captured
     * as an appointment ID.
     */
    @GetMapping("/appointments/overview")
    public ResponseEntity<AdminOverviewResponse> getOverview() {
        return ResponseEntity.ok(adminAppointmentService.getOverview());
    }

    /**
     * One appointment with the full status-change audit trail for support inspection.
     * Unknown IDs are 404. Authorization is URL-level: only ADMIN tokens reach here.
     */
    @GetMapping("/appointments/{id}")
    public ResponseEntity<AdminAppointmentDetailResponse> getAppointmentDetail(@PathVariable Long id) {
        return ResponseEntity.ok(adminAppointmentService.getAppointmentDetail(id));
    }

    /**
     * ADMIN support cancellation — the one admin appointment mutation, implemented
     * explicitly: a required reason, the same status rules as patient/doctor cancellation
     * (only SCHEDULED or CONFIRMED can be cancelled; otherwise 409), the authenticated
     * admin recorded in {@code changed_by_user_id}, and a history row — all atomic.
     * 404 unknown appointment, 400 missing reason, 409 invalid status.
     */
    @PostMapping("/appointments/{id}/cancel")
    public ResponseEntity<AdminAppointmentResponse> cancelAppointment(
            @PathVariable Long id,
            @Valid @RequestBody CancelAppointmentRequest request) {
        AdminAppointmentResponse response = adminAppointmentService
                .cancelAppointmentAsAdmin(id, request.cancellationReason());
        return ResponseEntity.ok(response);
    }
}
