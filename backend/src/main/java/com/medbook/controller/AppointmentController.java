package com.medbook.controller;

import com.medbook.dto.AppointmentResponse;
import com.medbook.dto.CreateAppointmentRequest;
import com.medbook.dto.CancelAppointmentRequest;
import com.medbook.dto.PatientBookingRequest;
import com.medbook.service.AppointmentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @GetMapping("/me")
    public ResponseEntity<List<AppointmentResponse>> getCurrentUserAppointments() {
        List<AppointmentResponse> appointments = appointmentService.getCurrentUserAppointments().stream()
                .map(AppointmentResponse::from)
                .toList();
        return ResponseEntity.ok(appointments);
    }

    @PostMapping
    public ResponseEntity<AppointmentResponse> createAppointment(@Valid @RequestBody PatientBookingRequest request) {
        AppointmentResponse response =
                AppointmentResponse.from(appointmentService.createAppointmentForCurrentUser(request));
        return created(response);
    }

    @PostMapping("/admin")
    public ResponseEntity<AppointmentResponse> createAppointmentForPatient(
            @Valid @RequestBody CreateAppointmentRequest request) {
        AppointmentResponse response =
                AppointmentResponse.from(appointmentService.createAppointmentForPatient(request));
        return created(response);
    }

    private ResponseEntity<AppointmentResponse> created(AppointmentResponse response) {
        return ResponseEntity.created(URI.create("/api/v1/appointments/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AppointmentResponse> getAppointment(@PathVariable Long id) {
        AppointmentResponse response = AppointmentResponse.from(appointmentService.getAppointment(id));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<AppointmentResponse> cancelAppointment(
            @PathVariable Long id,
            @Valid @RequestBody CancelAppointmentRequest request) {
        AppointmentResponse response =
                AppointmentResponse.from(appointmentService.cancelAppointmentForCurrentUser(id, request.cancellationReason()));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<AppointmentResponse> confirmAppointment(@PathVariable Long id) {
        AppointmentResponse response =
                AppointmentResponse.from(appointmentService.confirmAppointmentForCurrentUser(id));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<AppointmentResponse> completeAppointment(@PathVariable Long id) {
        AppointmentResponse response =
                AppointmentResponse.from(appointmentService.completeAppointmentForCurrentUser(id));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/no-show")
    public ResponseEntity<AppointmentResponse> markNoShow(@PathVariable Long id) {
        AppointmentResponse response =
                AppointmentResponse.from(appointmentService.markNoShowForCurrentUser(id));
        return ResponseEntity.ok(response);
    }
}
