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

/**
 * ADMIN appointment management: clinic-wide visibility, filtering, inspection and one
 * explicitly implemented, audited support action (cancellation).
 *
 * <p><strong>What an admin may and may not do.</strong> The doctor workflow
 * (confirm / complete / no-show) is deliberately <em>not</em> granted to admins — those
 * transitions live in {@link AppointmentService} and require the assigned doctor. What
 * this service adds is exactly:</p>
 * <ul>
 * <li>read the whole clinic's appointments with filters (date, status, doctor, patient);</li>
 * <li>read one appointment with its full status-change audit trail;</li>
 * <li>read the real overview counts behind the admin dashboard;</li>
 * <li>cancel an appointment as an explicit support action, with a required reason,
 *     a row lock, the authenticated admin recorded in {@code changed_by_user_id},
 *     and a history row — all in one transaction.</li>
 * </ul>
 *
 * <p>Reads use {@code readOnly} transactions and fetch-joined queries (OSIV is disabled);
 * the mutation reuses the same pessimistic row lock
 * ({@code SELECT ... FOR UPDATE}) and the same transition rules as patient/doctor
 * cancellation in {@link AppointmentService}, so an admin can cancel only from
 * SCHEDULED/CONFIRMED, exactly like everyone else — finishing states (COMPLETED,
 * NO_SHOW) and already-cancelled rows are rejected with 409.</p>
 */
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

    // ------------------------------------------------------------------
    // Reads
    // ------------------------------------------------------------------

    /**
     * Clinic-wide appointment list with every filter optional.
     *
     * @param appointmentDate exact clinic date, or {@code null} for any date
     * @param status          lifecycle status, or {@code null} for any status
     * @param doctorId        doctor ID, or {@code null} for every doctor
     * @param patientId       patient ID, or {@code null} for every patient
     * @return matching appointments mapped to the admin DTO (participants fetch-joined
     *         by the query, so mapping is safe after the transaction ends)
     */
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

    /**
     * One appointment with its full status-change audit trail.
     *
     * @throws AppointmentNotFoundException when the appointment does not exist (404)
     */
    @Transactional(readOnly = true)
    public AdminAppointmentDetailResponse getAppointmentDetail(Long appointmentId) {
        Appointment appointment = appointmentRepository.findByIdForAdmin(appointmentId)
                .orElseThrow(() -> new AppointmentNotFoundException(appointmentId));

        // The actor's display name is resolved inside this transaction; the entity's
        // changedByUser proxy is only readable while the session is open.
        List<AppointmentStatusHistoryResponse> history = statusHistoryRepository
                .findByAppointmentIdOrderByChangedAtAsc(appointmentId)
                .stream()
                .map(AppointmentStatusHistoryResponse::from)
                .toList();

        return AdminAppointmentDetailResponse.from(appointment, history);
    }

    /**
     * Real clinic overview for the admin dashboard: today's appointment count plus the
     * appointments-per-status breakdown (with explicit zeros), all from two aggregate
     * queries — no estimated numbers anywhere.
     */
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

    // ------------------------------------------------------------------
    // Admin support action: cancellation
    // ------------------------------------------------------------------

    /**
     * Cancel one appointment as an explicit ADMIN support action.
     *
     * <p>Flow mirrors {@code AppointmentService#cancelAppointmentForCurrentUser}: resolve
     * the caller from the JWT, lock the row with {@code SELECT ... FOR UPDATE}, validate
     * the transition (SCHEDULED/CONFIRMED → CANCELLED; anything else is 409, matching the
     * patient/doctor rules — admins get no extra power here), apply, and append a history
     * row whose {@code changed_by_user_id} is the authenticated admin. The status update
     * and the audit row commit atomically.</p>
     *
     * <p>The route is ADMIN-only by the security configuration
     * ({@code /api/v1/admin/** → hasRole(ADMIN)}); the role check here is defense in depth
     * so the service can never be invoked with a weaker caller.</p>
     *
     * @param appointmentId      the appointment to cancel
     * @param cancellationReason why the clinic support staff cancelled it (required)
     * @return the cancelled appointment, mapped to the admin DTO
     * @throws AppointmentNotFoundException      when the appointment does not exist (404)
     * @throws IllegalArgumentException          when no reason is supplied (400)
     * @throws InvalidAppointmentStatusException when the status forbids cancellation (409)
     */
    @Transactional
    public AdminAppointmentResponse cancelAppointmentAsAdmin(Long appointmentId, String cancellationReason) {
        User admin = requireAdmin();

        if (cancellationReason == null || cancellationReason.isBlank()) {
            throw new IllegalArgumentException("Cancellation reason is required");
        }

        // Same lock pattern as every other transition: two concurrent cancels serialize
        // on the row lock, and the loser sees the winner's CANCELLED status and fails 409.
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

        // Audit trail: append the new status with the real authenticated admin as actor.
        recordStatusChange(saved, AppointmentStatus.CANCELLED, cancellationReason, admin);

        // Neither participant caused the cancellation, so BOTH are notified — same
        // transaction as the cancellation itself.
        String actor = NotificationService.actorName(admin);
        notificationService.notifyPatient(saved, NotificationType.APPOINTMENT_CANCELLED, actor);
        notificationService.notifyDoctor(saved, NotificationType.APPOINTMENT_CANCELLED, actor);

        return AdminAppointmentResponse.from(saved);
    }

    /**
     * The authenticated caller as a user row, requiring the ADMIN role.
     *
     * <p>Identity comes from the JWT only — never from the request. The role assertion is
     * defense in depth on top of the URL-level rule.</p>
     */
    private User requireAdmin() {
        Long adminId = currentUserProvider.requireCurrentUserId();
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new AppointmentOwnershipException("Authenticated account no longer exists"));
        if (!admin.hasRole("ADMIN")) {
            throw new AppointmentOwnershipException("Only administrators can perform this action");
        }
        return admin;
    }

    /**
     * Append one status history row. History is append-only, exactly like the
     * {@code AppointmentService} audit writes.
     */
    private void recordStatusChange(Appointment appointment, AppointmentStatus status, String notes, User changedBy) {
        AppointmentStatusHistory history = new AppointmentStatusHistory(appointment, status);
        history.setNotes(notes);
        history.setChangedByUser(changedBy);
        statusHistoryRepository.save(history);
    }
}
