package com.medbook.controller;

import com.medbook.dto.AppointmentResponse;
import com.medbook.dto.CancelAppointmentRequest;
import com.medbook.dto.CreateAppointmentRequest;
import com.medbook.entity.Appointment;
import com.medbook.service.AppointmentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * REST API for appointments.
 *
 * <p>The controller is intentionally thin. Its only jobs are:</p>
 * <ol>
 * <li>accept and validate the request body,</li>
 * <li>call {@link AppointmentService} — the single owner of the booking rules,</li>
 * <li>map the returned entity to {@link AppointmentResponse},</li>
 * <li>choose the HTTP status.</li>
 * </ol>
 *
 * <p>There is deliberately no availability, overlap, or status logic here: a controller that
 * decides whether a doctor is free would duplicate the service rules and would have to open
 * its own transaction to do so safely.</p>
 *
 * <h3>Endpoints</h3>
 * <pre>
 * POST /api/v1/appointments            -&gt; 201 Created  (Location header points at the new appointment)
 * GET  /api/v1/appointments/{id}       -&gt; 200 OK
 * POST /api/v1/appointments/{id}/cancel-&gt; 200 OK
 * </pre>
 *
 * <p>Failed requests are turned into a consistent {@link com.medbook.dto.ErrorResponse} body
 * by {@link com.medbook.exception.GlobalExceptionHandler}, for example
 * {@code 404} for an unknown doctor/patient/appointment, {@code 409} for a business conflict
 * (doctor unavailable, slot taken, illegal status change) and {@code 400} for an invalid
 * request.</p>
 *
 * <p><strong>Development note:</strong> authentication and authorization are not part of this
 * step, so these endpoints currently accept anonymous requests. Cancellation history rows are
 * therefore written with {@code changed_by_user_id = NULL} instead of a fabricated user.</p>
 */
@RestController
@RequestMapping(AppointmentController.BASE_PATH)
public class AppointmentController {

    /** Versioned base path shared with {@code HelloController} ({@code /api/v1}). */
    static final String BASE_PATH = "/api/v1/appointments";

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    /**
     * Book a new appointment.
     *
     * @param request the booking request (patient, doctor, date, start time, end time)
     * @return {@code 201 Created} with the stored appointment and a {@code Location} header
     */
    @PostMapping
    public ResponseEntity<AppointmentResponse> createAppointment(
            @Valid @RequestBody CreateAppointmentRequest request
    ) {
        Appointment created = appointmentService.createAppointment(
                request.patientId(),
                request.doctorId(),
                request.appointmentDate(),
                request.startTime(),
                request.endTime()
        );

        return ResponseEntity
                .created(URI.create(BASE_PATH + "/" + created.getId()))
                .body(AppointmentResponse.from(created));
    }

    /**
     * Read a single appointment.
     *
     * @param id the appointment ID
     * @return {@code 200 OK} with the appointment, or {@code 404} when it does not exist
     */
    @GetMapping("/{id}")
    public AppointmentResponse getAppointment(@PathVariable Long id) {
        return AppointmentResponse.from(appointmentService.getAppointment(id));
    }

    /**
     * Cancel an appointment.
     *
     * <p>Returns the updated resource instead of {@code 204 No Content} so the caller can see
     * the new status and the recorded cancellation reason without a second request.</p>
     *
     * @param id      the appointment to cancel
     * @param request the cancellation request, which must contain a reason
     * @return {@code 200 OK} with the cancelled appointment, {@code 404} when unknown,
     *         {@code 409} when the current status cannot be cancelled, {@code 400} when the
     *         reason is missing
     */
    @PostMapping("/{id}/cancel")
    public AppointmentResponse cancelAppointment(
            @PathVariable Long id,
            @Valid @RequestBody CancelAppointmentRequest request
    ) {
        return AppointmentResponse.from(
                appointmentService.cancelAppointment(id, request.cancellationReason()));
    }
}
