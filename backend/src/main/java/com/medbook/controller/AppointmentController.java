package com.medbook.controller;

import com.medbook.dto.AppointmentResponse;
import com.medbook.dto.CreateAppointmentRequest;
import com.medbook.dto.CancelAppointmentRequest;
import com.medbook.dto.PatientBookingRequest;
import com.medbook.service.AppointmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Appointment REST API.
 *
 * <h3>How the caller's identity reaches the service</h3>
 * This controller never reads a caller identity from the request body or query string.
 * The authenticated user is resolved from the JWT (via {@code CurrentUserProvider} in the
 * service layer). Anything a client writes into {@code patientId} is advisory at best:
 * for patient callers it is ignored entirely, for admins it must match the support rules
 * documented in {@code docs/doctor-discovery-and-booking.md}.
 */
@RestController
@RequestMapping("/api/v1/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    /**
     * List the authenticated caller's appointments.
     * Patients see bookings made for them, doctors see their schedule, admins get 403
     * (no admin listing exists — see {@code AppointmentService#getCurrentUserAppointments}).
     */
    @GetMapping("/me")
    public ResponseEntity<List<AppointmentResponse>> getCurrentUserAppointments() {
        List<AppointmentResponse> appointments = appointmentService.getCurrentUserAppointments().stream()
                .map(AppointmentResponse::from)
                .toList();
        return ResponseEntity.ok(appointments);
    }

    /**
     * Book an appointment — <strong>two request shapes, chosen by role on the server</strong>.
     *
     * <ul>
     *   <li><strong>PATIENT</strong> sends {@link PatientBookingRequest}: no {@code patientId}
     *       at all. The service books for the patient profile linked to the JWT caller.</li>
     *   <li><strong>ADMIN</strong> sends {@link CreateAppointmentRequest} (with
     *       {@code patientId}): the support path for booking on behalf of a patient.</li>
     * </ul>
     *
     * <p>The shape is decided by the caller's <em>role</em>, not by what the client
     * happens to send — a patient's {@code patientId} (if present) is never read.</p>
     */
    @PostMapping
    public ResponseEntity<AppointmentResponse> createAppointment(@Valid @RequestBody PatientBookingRequest request) {
        AppointmentResponse response =
                AppointmentResponse.from(appointmentService.createAppointmentForCurrentUser(request));
        return created(response);
    }

    /**
     * Admin support path: book an appointment for a specified patient.
     * Patient callers are rejected here because {@code POST /api/v1/appointments} is
     * mapped to the self-booking shape; sending a patient token to this route yields
     * 403 rather than an identity that could be fabricated from the body.
     */
    @PostMapping("/admin")
    public ResponseEntity<AppointmentResponse> createAppointmentForPatient(
            @Valid @RequestBody CreateAppointmentRequest request) {
        AppointmentResponse response =
                AppointmentResponse.from(appointmentService.createAppointmentForPatient(request));
        return created(response);
    }

    /** 201 with a Location header pointing at the new resource. */
    private ResponseEntity<AppointmentResponse> created(AppointmentResponse response) {
        return ResponseEntity.created(URI.create("/api/v1/appointments/" + response.id())).body(response);
    }

    /**
     * Fetch one appointment with server-side ownership authorization:
     * patients may read only their own bookings, doctors only appointments assigned
     * to them, admins any appointment (support rule). Unknown IDs are 404;
     * unauthorized access is 403.
     */
    @GetMapping("/{id}")
    public ResponseEntity<AppointmentResponse> getAppointment(@PathVariable Long id) {
        AppointmentResponse response = AppointmentResponse.from(appointmentService.getAppointment(id));
        return ResponseEntity.ok(response);
    }

    /**
     * Cancel an appointment as the authenticated caller. The cancellation history row
     * records the caller's real user ID ({@code changed_by_user_id}); patients cancel
     * only their own bookings, doctors only appointments assigned to them, admins
     * are rejected (policy deliberately pending).
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<AppointmentResponse> cancelAppointment(
            @PathVariable Long id,
            @Valid @RequestBody CancelAppointmentRequest request) {
        AppointmentResponse response =
                AppointmentResponse.from(appointmentService.cancelAppointmentForCurrentUser(id, request.cancellationReason()));
        return ResponseEntity.ok(response);
    }

    /**
     * Confirm an appointment (SCHEDULED → CONFIRMED) — assigned DOCTOR only.
     *
     * <p>Status mapping: 200 on success; 401 unauthenticated; 403 caller is not a doctor
     * or is not the assigned doctor; 404 unknown appointment; 409 the appointment is not
     * SCHEDULED (invalid transition, {@link com.medbook.service.InvalidAppointmentStatusException}).
     * The history row records the authenticated doctor as {@code changed_by_user_id}.</p>
     */
    @PostMapping("/{id}/confirm")
    public ResponseEntity<AppointmentResponse> confirmAppointment(@PathVariable Long id) {
        AppointmentResponse response =
                AppointmentResponse.from(appointmentService.confirmAppointmentForCurrentUser(id));
        return ResponseEntity.ok(response);
    }

    /**
     * Complete an appointment (CONFIRMED → COMPLETED) — assigned DOCTOR only.
     * Same status mapping as confirm; 409 when the appointment is not CONFIRMED.
     */
    @PostMapping("/{id}/complete")
    public ResponseEntity<AppointmentResponse> completeAppointment(@PathVariable Long id) {
        AppointmentResponse response =
                AppointmentResponse.from(appointmentService.completeAppointmentForCurrentUser(id));
        return ResponseEntity.ok(response);
    }

    /**
     * Mark an appointment as a no-show (CONFIRMED → NO_SHOW) — assigned DOCTOR only.
     * Same status mapping as confirm; 409 when the appointment is not CONFIRMED.
     */
    @PostMapping("/{id}/no-show")
    public ResponseEntity<AppointmentResponse> markNoShow(@PathVariable Long id) {
        AppointmentResponse response =
                AppointmentResponse.from(appointmentService.markNoShowForCurrentUser(id));
        return ResponseEntity.ok(response);
    }
}
