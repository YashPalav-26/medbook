package com.medbook.service;

import com.medbook.entity.Appointment;
import com.medbook.entity.AppointmentStatus;
import com.medbook.entity.AppointmentStatusHistory;
import com.medbook.entity.Doctor;
import com.medbook.entity.DoctorAvailability;
import com.medbook.entity.Patient;
import com.medbook.repository.AppointmentRepository;
import com.medbook.repository.AppointmentStatusHistoryRepository;
import com.medbook.repository.DoctorAvailabilityRepository;
import com.medbook.repository.DoctorRepository;
import com.medbook.repository.PatientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

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

    private final AppointmentRepository appointmentRepository;
    private final AppointmentStatusHistoryRepository statusHistoryRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final DoctorAvailabilityRepository availabilityRepository;

    public AppointmentService(
            AppointmentRepository appointmentRepository,
            AppointmentStatusHistoryRepository statusHistoryRepository,
            DoctorRepository doctorRepository,
            PatientRepository patientRepository,
            DoctorAvailabilityRepository availabilityRepository
    ) {
        this.appointmentRepository = appointmentRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.doctorRepository = doctorRepository;
        this.patientRepository = patientRepository;
        this.availabilityRepository = availabilityRepository;
    }

    /**
     * Read one appointment by ID.
     *
     * <p>This is a plain read used by the REST layer for {@code GET /api/v1/appointments/{id}};
     * it contains no business rules beyond "the appointment must exist". It runs in a
     * read-only transaction and fetches patient and doctor eagerly so the caller can map the
     * entity to a response DTO after the transaction has ended.</p>
     *
     * @param appointmentId the appointment to read
     * @return the stored appointment
     * @throws AppointmentNotFoundException when no appointment row exists for that ID
     */
    @Transactional(readOnly = true)
    public Appointment getAppointment(Long appointmentId) {
        return appointmentRepository.findByIdWithPatientAndDoctor(appointmentId)
                .orElseThrow(() -> new AppointmentNotFoundException(appointmentId));
    }

    /**
     * Book a new appointment.
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
     * @param patientId       the booking patient's ID
     * @param doctorId        the booked doctor's ID
     * @param appointmentDate the clinic visit date
     * @param startTime       window start time
     * @param endTime         window end time (exclusive, must be after {@code startTime})
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
            LocalTime endTime
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
        Appointment saved = appointmentRepository.save(appointment);

        // 8. Record the first status history row: the appointment starts as SCHEDULED.
        recordStatusChange(saved, AppointmentStatus.SCHEDULED, "Appointment created");

        return saved;
    }

    /**
     * Cancel an existing appointment.
     *
     * <p>Business flow (all inside one transaction):</p>
     * <ol>
     * <li>lock the appointment row with {@code SELECT ... FOR UPDATE};</li>
     * <li>reject the request when the appointment is already in a terminal state;</li>
     * <li>set status to CANCELLED and store the reason (the schema CHECK constraint
     *     requires a reason whenever status = 'CANCELLED');</li>
     * <li>append a new status history row without touching earlier ones.</li>
     * </ol>
     *
     * @param appointmentId      the appointment to cancel
     * @param cancellationReason why the appointment is cancelled (required)
     * @return the updated appointment, with status CANCELLED
     * @throws AppointmentNotFoundException      when the appointment ID does not exist
     * @throws InvalidAppointmentStatusException when the current status cannot become CANCELLED
     * @throws IllegalArgumentException          when no cancellation reason is supplied
     */
    @Transactional
    public Appointment cancelAppointment(Long appointmentId, String cancellationReason) {
        if (cancellationReason == null || cancellationReason.isBlank()) {
            throw new IllegalArgumentException("Cancellation reason is required");
        }

        // Lock the row first: the read-check-write below must be atomic.
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

        // Audit trail: append the new status, never delete or rewrite history rows.
        recordStatusChange(saved, AppointmentStatus.CANCELLED, cancellationReason);

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
     * <p>The actor ({@code changedByUser}) is intentionally left null until the
     * authentication step exists to identify the caller.</p>
     */
    private void recordStatusChange(Appointment appointment, AppointmentStatus status, String notes) {
        AppointmentStatusHistory history = new AppointmentStatusHistory(appointment, status);
        history.setNotes(notes);
        statusHistoryRepository.save(history);
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
