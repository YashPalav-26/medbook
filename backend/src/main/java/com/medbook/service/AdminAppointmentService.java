package com.medbook.service;

import com.medbook.dto.AdminAppointmentDetailResponse;
import com.medbook.dto.AdminAppointmentResponse;
import com.medbook.dto.AdminOverviewResponse;
import com.medbook.dto.AppointmentStatusHistoryResponse;
import com.medbook.entity.Appointment;
import com.medbook.entity.AppointmentStatus;
import com.medbook.entity.AppointmentStatusHistory;
import com.medbook.entity.NotificationType;
import com.medbook.entity.User;
import com.medbook.repository.AppointmentRepository;
import com.medbook.repository.AppointmentStatusHistoryRepository;
import com.medbook.repository.UserRepository;
import com.medbook.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminAppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentStatusHistoryRepository statusHistoryRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;
    private final NotificationService notificationService;

    public AdminAppointmentService(AppointmentRepository appointmentRepository,
                                   AppointmentStatusHistoryRepository statusHistoryRepository,
                                   UserRepository userRepository,
                                   CurrentUserProvider currentUserProvider,
                                   NotificationService notificationService) {
        this.appointmentRepository = appointmentRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public List<AdminAppointmentResponse> listAppointments(LocalDate appointmentDate,
                                                           AppointmentStatus status,
                                                           Long doctorId,
                                                           Long patientId) {
        return appointmentRepository
                .findAdminAppointments(appointmentDate, status, doctorId, patientId)
                .stream()
                .map(AdminAppointmentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminAppointmentDetailResponse getAppointmentDetail(Long appointmentId) {
        Appointment appointment = appointmentRepository.findByIdForAdmin(appointmentId)
                .orElseThrow(() -> new AppointmentNotFoundException(appointmentId));

        List<AppointmentStatusHistoryResponse> history = statusHistoryRepository
                .findByAppointmentIdOrderByChangedAtAsc(appointmentId)
                .stream()
                .map(AppointmentStatusHistoryResponse::from)
                .toList();

        return AdminAppointmentDetailResponse.from(appointment, history);
    }

    @Transactional(readOnly = true)
    public AdminOverviewResponse getOverview() {
        LocalDate today = LocalDate.now();

        Map<AppointmentStatus, Long> counts = new EnumMap<>(AppointmentStatus.class);
        for (AppointmentStatus appointmentStatus : AppointmentStatus.values()) {
            counts.put(appointmentStatus, 0L);
        }
        appointmentRepository.countGroupedByStatus()
                .forEach(row -> counts.put(row.getStatus(), row.getTotal()));

        Map<String, Long> statusCounts = new java.util.LinkedHashMap<>();
        counts.forEach((appointmentStatus, total) -> statusCounts.put(appointmentStatus.name(), total));

        long total = statusCounts.values().stream().mapToLong(Long::longValue).sum();

        return new AdminOverviewResponse(
                appointmentRepository.countByAppointmentDate(today),
                today,
                statusCounts,
                total
        );
    }

    @Transactional
    public AdminAppointmentResponse cancelAppointmentAsAdmin(Long appointmentId, String cancellationReason) {
        User admin = requireAdmin();

        if (cancellationReason == null || cancellationReason.isBlank()) {
            throw new IllegalArgumentException("Cancellation reason is required");
        }

        Appointment appointment = appointmentRepository.findByIdForUpdate(appointmentId)
                .orElseThrow(() -> new AppointmentNotFoundException(appointmentId));

        AppointmentStatus currentStatus = appointment.getStatus();
        if (currentStatus == AppointmentStatus.CANCELLED) {
            throw new InvalidAppointmentStatusException(
                    currentStatus, AppointmentStatus.CANCELLED, "appointment is already cancelled");
        }
        if (currentStatus == AppointmentStatus.COMPLETED || currentStatus == AppointmentStatus.NO_SHOW) {
            throw new InvalidAppointmentStatusException(
                    currentStatus, AppointmentStatus.CANCELLED,
                    "a finished appointment (COMPLETED or NO_SHOW) cannot be cancelled");
        }

        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointment.setCancellationReason(cancellationReason);
        Appointment saved = appointmentRepository.save(appointment);

        recordStatusChange(saved, AppointmentStatus.CANCELLED, cancellationReason, admin);

        String actor = NotificationService.actorName(admin);
        notificationService.notifyPatient(saved, NotificationType.APPOINTMENT_CANCELLED, actor);
        notificationService.notifyDoctor(saved, NotificationType.APPOINTMENT_CANCELLED, actor);

        return AdminAppointmentResponse.from(saved);
    }

    private User requireAdmin() {
        Long adminId = currentUserProvider.requireCurrentUserId();
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new AppointmentOwnershipException("Authenticated account no longer exists"));
        if (!admin.hasRole("ADMIN")) {
            throw new AppointmentOwnershipException("Only administrators can perform this action");
        }
        return admin;
    }

    private void recordStatusChange(Appointment appointment, AppointmentStatus status, String notes, User changedBy) {
        AppointmentStatusHistory history = new AppointmentStatusHistory(appointment, status);
        history.setNotes(notes);
        history.setChangedByUser(changedBy);
        statusHistoryRepository.save(history);
    }
}
