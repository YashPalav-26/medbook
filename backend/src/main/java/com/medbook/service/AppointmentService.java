package com.medbook.service;

import com.medbook.entity.Appointment;
import com.medbook.entity.AppointmentStatus;
import com.medbook.entity.AppointmentStatusHistory;
import com.medbook.entity.Doctor;
import com.medbook.entity.DoctorAvailability;
import com.medbook.entity.Patient;
import com.medbook.entity.User;
import com.medbook.repository.AppointmentRepository;
import com.medbook.repository.AppointmentStatusHistoryRepository;
import com.medbook.repository.DoctorAvailabilityRepository;
import com.medbook.repository.DoctorRepository;
import com.medbook.repository.PatientRepository;
import com.medbook.repository.UserRepository;
import com.medbook.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * Service (business logic) layer for appointments.
 *
 * <p>This class is the single place where the rules of "booking an appointment" live:
 * who is allowed to book, which time windows are bookable, which existing appointments
 * block a slot, and what gets written to the audit trail. Repositories only read and
 * write rows; they do not decide anything.</p>
 *
 * <p>Both public methods are {@link Transactional}, so all their database work happens
 * inside one database transaction that is committed at the end or rolled back entirely
 * when an exception escapes. That guarantee is what makes "create appointment + create
 * status history" atomic: a half-written booking can never be committed.</p>
 *
 * <p>Booking also takes a pessimistic row lock on the doctor
 * ({@code SELECT ... FOR UPDATE}) before checking for conflicts, so two concurrent
 * bookings for the same doctor cannot both pass the overlap check.</p>
 */
@Service
public class AppointmentService {

    /**
     * MySQL stores {@code day_of_week} as 0 = Sunday ... 6 = Saturday, while Java's
     * {@link java.time.DayOfWeek#getValue()} returns 1 = Monday ... 7 = Sunday.
     */
    private static final int JAVA_SUNDAY = 7;

    /** Role names exactly as stored in the {@code roles} table (no {@code ROLE_} prefix). */
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_DOCTOR = "DOCTOR";
    private static final String ROLE_PATIENT = "PATIENT";

    /** Message for a rejected ownership read/write — deliberately does not echo the ID. */
    private static final String ACCESS_DENIED_TO_APPOINTMENT =
            "You do not have access to this appointment";

    private final AppointmentRepository appointmentRepository;
    private final AppointmentStatusHistoryRepository statusHistoryRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final DoctorAvailabilityRepository availabilityRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    public AppointmentService(
            AppointmentRepository appointmentRepository,
            AppointmentStatusHistoryRepository statusHistoryRepository,
            DoctorRepository doctorRepository,
            PatientRepository patientRepository,
            DoctorAvailabilityRepository availabilityRepository,
            UserRepository userRepository,
            CurrentUserProvider currentUserProvider
    ) {
        this.appointmentRepository = appointmentRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.doctorRepository = doctorRepository;
        this.patientRepository = patientRepository;
        this.availabilityRepository = availabilityRepository;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
    }

    /**
     * Read one appointment by ID, enforcing ownership for the authenticated caller.
     *
     * <p>Authorization rules (documented in {@code docs/appointment-authorization.md}):</p>
     * <ul>
     * <li><strong>PATIENT</strong> — only their own appointments;</li>
     * <li><strong>DOCTOR</strong> — only appointments assigned to them;</li>
     * <li><strong>ADMIN</strong> — any appointment (administrative support role).</li>
     * </ul>
     *
     * <p>An appointment that exists but is not visible to the caller is rejected with
     * {@link AppointmentOwnershipException} (HTTP 403) — the caller is authenticated, so
     * this is a permission problem, not a missing-row problem. A non-existent row stays a
     * 404 {@link AppointmentNotFoundException}. Both paths are enforced here on the server;
     * the React UI merely reflects what this method allows.</p>
     *
     * @param appointmentId the appointment to read
     * @return the stored appointment
     * @throws AppointmentNotFoundException     when no appointment row exists for that ID
     * @throws AppointmentOwnershipException    when the caller exists but may not see it
     * @throws org.springframework.security.authentication.InsufficientAuthenticationException
     *         when there is no authenticated caller
     */
    @Transactional(readOnly = true)
    public Appointment getAppointment(Long appointmentId) {
        User caller = requireCurrentUser();

        Appointment appointment = appointmentRepository.findByIdWithPatientAndDoctor(appointmentId)
                .orElseThrow(() -> new AppointmentNotFoundException(appointmentId));

        if (!canAccess(caller, appointment)) {
            throw new AppointmentOwnershipException(ACCESS_DENIED_TO_APPOINTMENT);
        }
        return appointment;
    }

    /**
     * All appointments of the authenticated caller, per role.
     *
     * <ul>
     * <li><strong>PATIENT</strong> — the appointments they booked;</li>
     * <li><strong>DOCTOR</strong> — the appointments assigned to them;</li>
     * <li><strong>ADMIN</strong> — rejected: a global appointment feed is an
     *     administrative feature that does not exist yet, so it fails loudly (403)
     *     instead of silently returning everything.</li>
     * </ul>
     *
     * @return the caller's appointments, newest first
     * @throws AppointmentOwnershipException when the caller's role has no listing yet (ADMIN)
     * @throws org.springframework.security.authentication.InsufficientAuthenticationException
     *         when there is no authenticated caller
     */
    @Transactional(readOnly = true)
    public List<Appointment> getCurrentUserAppointments() {
        User caller = requireCurrentUser();

        if (caller.hasRole(ROLE_PATIENT)) {
            return appointmentRepository.findAllByPatientUserId(caller.getId());
        }
        if (caller.hasRole(ROLE_DOCTOR)) {
            return appointmentRepository.findAllByDoctorUserId(caller.getId());
        }
        throw new AppointmentOwnershipException(
                "Appointment listing is not available for this account role");
    }

    /**
     * Book a new appointment for the <strong>authenticated patient</strong> (self-booking).
     *
     * <p>This is the entry point for {@code POST /api/v1/appointments} with a
     * {@link com.medbook.dto.PatientBookingRequest} body — a body that carries no
     * {@code patientId} at all. The booking patient is resolved from the JWT caller:
     * {@code users.id} → the patient profile linked to that account. A client cannot
     * book for someone else because there is simply no field it could influence —
     * identity is never taken from request data.</p>
     *
     * <p>Doctors are rejected (they have no patient profile; booking is not a doctor
     * capability in this project), admins are pointed at the support path
     * {@link #createAppointmentForPatient(com.medbook.dto.CreateAppointmentRequest)}.
     * All slot/availability/conflict validation and the doctor row lock are the same
     * rules used by every other booking path.</p>
     *
     * @param request the self-booking request ({@code doctorId}, date, time window, optional reason)
     * @return the persisted appointment, with status SCHEDULED
     * @throws AppointmentOwnershipException when the caller is not a patient (DOCTOR/ADMIN)
     * @throws IllegalStateException         when the authenticated account has no patient profile
     * @throws PatientInactiveException      when the caller's patient profile is deactivated
     * @throws DoctorNotFoundException       when no doctor row exists for {@code doctorId}
     * @throws DoctorInactiveException       when the doctor exists but is deactivated
     * @throws IllegalArgumentException      when the date/time window itself is invalid
     * @throws DoctorUnavailableException    when the window is outside doctor availability
     * @throws AppointmentConflictException  when the doctor already has a clashing appointment
     */
    @Transactional
    public Appointment createAppointmentForCurrentUser(com.medbook.dto.PatientBookingRequest request) {
        User caller = requireCurrentUser();

        if (!caller.hasRole(ROLE_PATIENT)) {
            throw new AppointmentOwnershipException(
                    "Only patients can book appointments for themselves;"
                            + " use the support path to book on behalf of a patient");
        }

        Patient patient = patientRepository.findByUserId(caller.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated account has no patient profile: " + caller.getId()));

        return createAppointment(
                patient.getId(),
                request.doctorId(),
                request.appointmentDate(),
                request.startTime(),
                request.endTime(),
                request.reason());
    }

    /**
     * Book a new appointment for an explicitly named patient — the ADMIN support path.
     *
     * <p>Justification for accepting {@code patientId} here (documented in
     * {@code docs/doctor-discovery-and-booking.md}): administrators operate the clinic
     * back office, where booking on behalf of a patient who called by phone or cannot
     * use the app is the entire point. The capability is restricted to the ADMIN role:
     * patients must use {@link #createAppointmentForCurrentUser}, where their identity
     * comes from the JWT and no {@code patientId} is accepted; doctors cannot book.</p>
     *
     * @param request the support request (includes the target {@code patientId})
     * @return the persisted appointment, with status SCHEDULED
     * @throws AppointmentOwnershipException when the caller is not an admin
     */
    @Transactional
    public Appointment createAppointmentForPatient(com.medbook.dto.CreateAppointmentRequest request) {
        User caller = requireCurrentUser();

        if (!caller.hasRole(ROLE_ADMIN)) {
            throw new AppointmentOwnershipException(
                    "Booking for another patient requires an administrator");
        }
        return createAppointment(
                request.patientId(),
                request.doctorId(),
                request.appointmentDate(),
                request.startTime(),
                request.endTime(),
                null);
    }

    /**
     * Book a new appointment for a patient by ID without an explicit reason.
     *
     * <p>Convenience overload kept for existing internal/test callers; the HTTP
     * request paths {@link #createAppointmentForCurrentUser} and
     * {@link #createAppointmentForPatient} provide the reason field.</p>
     */
    @Transactional
    public Appointment createAppointment(
            Long patientId,
            Long doctorId,
            LocalDate appointmentDate,
            LocalTime startTime,
            LocalTime endTime
    ) {
        return createAppointment(patientId, doctorId, appointmentDate, startTime, endTime, null);
    }

    /**
     * Core booking routine shared by every path (patient self-booking, admin support,
     * context-free internal use).
     *
     * <p>Business flow (all inside one transaction):</p>
     * <ol>
     * <li>lock the doctor row with {@code SELECT ... FOR UPDATE};</li>
     * <li>verify the doctor exists and is active;</li>
     * <li>verify the patient exists and is active;</li>
     * <li>validate the appointment date and time window;</li>
     * <li>verify the window falls inside the doctor's availability;</li>
     * <li>verify no existing appointment clashes with the window;</li>
     * <li>insert the appointment with a snapshot of the consultation fee;</li>
     * <li>insert the first status history row (SCHEDULED).</li>
     * </ol>
     *
     * @param patientId       the booking patient's ID (already resolved and authorized upstream)
     * @param doctorId        the booked doctor's ID
     * @param appointmentDate the clinic visit date
     * @param startTime       window start time
     * @param endTime         window end time (exclusive, must be after {@code startTime})
     * @param reason          optional visit reason stored with the appointment (may be null)
     * @return the persisted appointment, with status SCHEDULED
     * @throws DoctorNotFoundException      when no doctor row exists for {@code doctorId}
     * @throws DoctorInactiveException      when the doctor exists but is deactivated
     * @throws PatientNotFoundException     when no patient row exists for {@code patientId}
     * @throws PatientInactiveException     when the patient exists but is deactivated
     * @throws IllegalArgumentException     when the date/time window itself is invalid
     * @throws DoctorUnavailableException   when the window is outside doctor availability
     * @throws AppointmentConflictException when the doctor already has a clashing appointment
     */
    @Transactional
    public Appointment createAppointment(
            Long patientId,
            Long doctorId,
            LocalDate appointmentDate,
            LocalTime startTime,
            LocalTime endTime,
            String reason
    ) {
        // 1 + 2. Lock the doctor row, then check the doctor exists and is active.
        // The lock is held until this transaction commits or rolls back.
        Doctor doctor = doctorRepository.findByIdForUpdate(doctorId)
                .orElseThrow(() -> new DoctorNotFoundException(doctorId));

        if (!doctor.isActive()) {
            throw new DoctorInactiveException(doctorId);
        }

        // 3. Verify the patient exists and is active.
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new PatientNotFoundException(patientId));

        if (!patient.isActive()) {
            throw new PatientInactiveException(patientId);
        }

        // 4. Validate the requested date and time window.
        validateAppointmentWindow(appointmentDate, startTime, endTime);

        // 5. Verify the window is covered by the doctor's availability.
        ensureDoctorIsAvailable(doctor, appointmentDate, startTime, endTime);

        // 6. Verify no still-active appointment clashes with the window.
        ensureSlotIsFree(doctor, appointmentDate, startTime, endTime);

        // 7. Insert the appointment, snapshotting the consultation fee that applies now.
        Appointment appointment = new Appointment(
                patient,
                doctor,
                appointmentDate,
                startTime,
                endTime,
                doctor.getConsultationFee()
        );
        appointment.setReason(reason);
        Appointment saved = appointmentRepository.save(appointment);

        // 8. Record the first status history row: the appointment starts as SCHEDULED.
        // When a JWT caller exists (every HTTP request), they are recorded as the actor;
        // context-free internal/test callers legitimately produce NULL.
        recordStatusChange(saved, AppointmentStatus.SCHEDULED, "Appointment created",
                currentUserProvider.currentUserId().flatMap(userRepository::findById).orElse(null));

        return saved;
    }

    /**
     * Cancel one appointment on behalf of the authenticated caller (ownership enforced).
     *
     * <p>Rules mirror {@link #getAppointment(Long)}: patients cancel only their own
     * appointments, doctors only appointments assigned to them, and ADMIN cancellation is
     * deliberately not supported yet — an administrator silently cancelling a clinic visit
     * is a policy decision that has not been made, so the request is rejected (403) and
     * must be performed by the patient or the doctor. Unknown rows stay 404. The history
     * row records the real caller (never fabricated; never a user ID from the request).</p>
     *
     * @param appointmentId      the appointment to cancel
     * @param cancellationReason why the appointment is cancelled (required)
     * @return the updated appointment, with status CANCELLED
     * @throws AppointmentNotFoundException      when the appointment ID does not exist
     * @throws AppointmentOwnershipException     when the caller may not cancel it
     * @throws InvalidAppointmentStatusException when the current status cannot become CANCELLED
     * @throws IllegalArgumentException          when no cancellation reason is supplied
     * @throws org.springframework.security.authentication.InsufficientAuthenticationException
     *         when there is no authenticated caller
     */
    @Transactional
    public Appointment cancelAppointmentForCurrentUser(Long appointmentId, String cancellationReason) {
        User caller = requireCurrentUser();

        Appointment appointment = appointmentRepository.findByIdForUpdate(appointmentId)
                .orElseThrow(() -> new AppointmentNotFoundException(appointmentId));

        if (!canCancel(caller, appointment)) {
            throw new AppointmentOwnershipException(ACCESS_DENIED_TO_APPOINTMENT);
        }
        return cancelAppointment(appointment, caller, cancellationReason);
    }

    /**
     * Cancel an appointment without a security context.
     *
     * <p>Kept for internal/test callers that run without authentication (for example the
     * transactional-behaviour tests). The audit row is written with
     * {@code changed_by_user_id = NULL} because no caller legitimately exists — a NULL is
     * never fabricated into a user. HTTP requests must use
     * {@link #cancelAppointmentForCurrentUser(Long, String)}, which enforces ownership.</p>
     *
     * @param appointmentId      the appointment to cancel
     * @param cancellationReason why the appointment is cancelled (required)
     * @return the updated appointment, with status CANCELLED
     */
    @Transactional
    public Appointment cancelAppointment(Long appointmentId, String cancellationReason) {
        return cancelAppointment(appointmentRepository.findByIdForUpdate(appointmentId)
                .orElseThrow(() -> new AppointmentNotFoundException(appointmentId)), null, cancellationReason);
    }

    /**
     * Shared cancellation flow: validate, lock in the status change, audit with the caller
     * (nullable only when no authenticated caller legitimately exists).
     */
    private Appointment cancelAppointment(Appointment appointment, User caller, String cancellationReason) {
        if (cancellationReason == null || cancellationReason.isBlank()) {
            throw new IllegalArgumentException("Cancellation reason is required");
        }

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

        // Audit trail: append the new status, never delete or rewrite history rows.
        // changedByUser is the real authenticated caller, or NULL when there is none.
        recordStatusChange(saved, AppointmentStatus.CANCELLED, cancellationReason, caller);

        return saved;
    }

    // ------------------------------------------------------------------
    // Private business rules
    // ------------------------------------------------------------------

    /**
     * Reject date/time windows that can never be a valid booking.
     *
     * <p>These checks need no database access, so they raise plain
     * {@link IllegalArgumentException}: the request itself is malformed.</p>
     */
    private void validateAppointmentWindow(LocalDate appointmentDate, LocalTime startTime, LocalTime endTime) {
        if (appointmentDate == null || startTime == null || endTime == null) {
            throw new IllegalArgumentException("Appointment date, start time and end time are required");
        }

        if (appointmentDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Appointment date cannot be in the past: " + appointmentDate);
        }

        if (!startTime.isBefore(endTime)) {
            throw new IllegalArgumentException(
                    "Start time " + startTime + " must be before end time " + endTime);
        }
    }

    /**
     * Verify the requested window is fully covered by at least one availability slot
     * of the doctor on that weekday.
     */
    private void ensureDoctorIsAvailable(
            Doctor doctor,
            LocalDate appointmentDate,
            LocalTime startTime,
            LocalTime endTime
    ) {
        byte dayOfWeek = toMysqlDayOfWeek(appointmentDate);
        List<DoctorAvailability> slots =
                availabilityRepository.findByDoctorIdAndDayOfWeek(doctor.getId(), dayOfWeek);

        boolean covered = slots.stream()
                .anyMatch(slot -> coversAppointment(slot, appointmentDate, startTime, endTime));

        if (!covered) {
            throw new DoctorUnavailableException(doctor.getId(), appointmentDate, startTime, endTime);
        }
    }

    /**
     * Decide whether a single availability slot covers the requested window.
     *
     * <p>A slot covers the window when:</p>
     * <ol>
     * <li>the requested window is inside the slot's {@code startTime}-{@code endTime}; and</li>
     * <li>the requested date is inside the slot's effective date range:
     *     <ul>
     *     <li>recurring slots repeat every week, bounded by {@code effectiveFrom}
     *         and/or {@code effectiveUntil} when those columns are set (NULL = no bound);</li>
     *     <li>non-recurring slots are one-off exceptions, so they match only the single
     *         date stored in {@code effectiveFrom} (or {@code effectiveUntil} when only
     *         that column is set).</li>
     *     </ul>
     * </li>
     * </ol>
     */
    private boolean coversAppointment(
            DoctorAvailability slot,
            LocalDate appointmentDate,
            LocalTime startTime,
            LocalTime endTime
    ) {
        if (startTime.isBefore(slot.getStartTime()) || endTime.isAfter(slot.getEndTime())) {
            return false;
        }

        if (slot.isRecurring()) {
            boolean startsTooLate = slot.getEffectiveFrom() != null
                    && appointmentDate.isBefore(slot.getEffectiveFrom());
            boolean endsTooEarly = slot.getEffectiveUntil() != null
                    && appointmentDate.isAfter(slot.getEffectiveUntil());
            return !startsTooLate && !endsTooEarly;
        }

        LocalDate oneOffDate = slot.getEffectiveFrom() != null
                ? slot.getEffectiveFrom()
                : slot.getEffectiveUntil();

        return oneOffDate != null && appointmentDate.equals(oneOffDate);
    }

    /**
     * Reject the booking when the doctor already has an appointment that clashes.
     * Cancelled appointments are excluded because they release the slot.
     */
    private void ensureSlotIsFree(
            Doctor doctor,
            LocalDate appointmentDate,
            LocalTime startTime,
            LocalTime endTime
    ) {
        List<Appointment> conflicts = appointmentRepository.findConflictingAppointments(
                doctor.getId(),
                appointmentDate,
                startTime,
                endTime,
                AppointmentStatus.CANCELLED
        );

        if (!conflicts.isEmpty()) {
            throw new AppointmentConflictException(doctor.getId(), appointmentDate, startTime, endTime);
        }
    }

    /**
     * Append one status history row. History is append-only: a status change adds a
     * record and never removes an earlier one.
     *
     * @param changedBy the authenticated user who caused the change, or {@code null} only
     *                  when no caller legitimately exists (context-free internal/test path)
     */
    private void recordStatusChange(Appointment appointment, AppointmentStatus status, String notes, User changedBy) {
        AppointmentStatusHistory history = new AppointmentStatusHistory(appointment, status);
        history.setNotes(notes);
        history.setChangedByUser(changedBy);
        statusHistoryRepository.save(history);
    }

    // ------------------------------------------------------------------
    // Caller identity and ownership rules
    // ------------------------------------------------------------------

    /**
     * The authenticated user as a row in {@code users}, or an exception when there is no
     * authenticated caller. The JWT is the only identity source — a user ID supplied in a
     * request body or path is never trusted for authorization.
     */
    private User requireCurrentUser() {
        Long callerId = currentUserProvider.requireCurrentUserId();
        return userRepository.findById(callerId)
                .orElseThrow(() -> new AppointmentOwnershipException(
                        "Authenticated account no longer exists"));
    }

    /**
     * The ownership matrix, evaluated on the loaded entities.
     *
     * <ul>
     * <li>ADMIN → any appointment (administrative support);</li>
     * <li>DOCTOR → appointments assigned to that doctor;</li>
     * <li>PATIENT → appointments booked by that patient;</li>
     * <li>any other role combination → denied.</li>
     * </ul>
     */
    private boolean canAccess(User caller, Appointment appointment) {
        if (caller.hasRole(ROLE_ADMIN)) {
            return true;
        }
        if (caller.hasRole(ROLE_DOCTOR)) {
            return appointment.getDoctor().getUser().getId().equals(caller.getId());
        }
        if (caller.hasRole(ROLE_PATIENT)) {
            return appointment.getPatient().getUser().getId().equals(caller.getId());
        }
        return false;
    }

    /**
     * Who may cancel: the same ownership matrix minus ADMIN.
     *
     * <p>Cancellation changes a patient's clinic visit, so the project deliberately does
     * not let an administrator do it yet — the operation is reserved for the patient who
     * booked (or the assigned doctor). When admin cancellation becomes a product decision,
     * this is the single place to change.</p>
     */
    private boolean canCancel(User caller, Appointment appointment) {
        if (caller.hasRole(ROLE_ADMIN)) {
            return false;
        }
        return canAccess(caller, appointment);
    }

    /**
     * Convert a date's weekday to the MySQL {@code day_of_week} convention used by the
     * {@code doctor_availability} table: 0 = Sunday ... 6 = Saturday.
     */
    private static byte toMysqlDayOfWeek(LocalDate date) {
        int javaDayOfWeek = date.getDayOfWeek().getValue(); // 1 = Monday ... 7 = Sunday
        return (byte) (javaDayOfWeek == JAVA_SUNDAY ? 0 : javaDayOfWeek);
    }
}
