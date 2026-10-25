package com.medbook.repository;

import com.medbook.entity.Appointment;
import com.medbook.entity.AppointmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Appointment} entities.
 *
 * <p>Provides derived query methods for common appointment lookups,
 * following Spring Data JPA naming conventions.</p>
 */
@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    /**
     * Find all appointments for a given doctor.
     *
     * <p>Generated query: {@code SELECT * FROM appointments WHERE doctor_id = ?}</p>
     *
     * @param doctorId the doctor's ID
     * @return list of appointments for that doctor
     */
    List<Appointment> findByDoctorId(Long doctorId);

    /**
     * Find all appointments for a given patient.
     *
     * <p>Generated query: {@code SELECT * FROM appointments WHERE patient_id = ?}</p>
     *
     * @param patientId the patient's ID
     * @return list of appointments for that patient
     */
    List<Appointment> findByPatientId(Long patientId);

    /**
     * Find all appointments with a given status.
     *
     * <p>Generated query: {@code SELECT * FROM appointments WHERE status = ?}</p>
     *
     * @param status the appointment status to filter by
     * @return list of appointments with that status
     */
    List<Appointment> findByStatus(AppointmentStatus status);

    /**
     * Find all appointments for a given doctor on a specific date.
     *
     * <p>Generated query: {@code SELECT * FROM appointments WHERE doctor_id = ? AND appointment_date = ?}</p>
     *
     * @param doctorId the doctor's ID
     * @param appointmentDate the appointment date
     * @return list of appointments for that doctor on that date
     */
    List<Appointment> findByDoctorIdAndAppointmentDate(Long doctorId, LocalDate appointmentDate);

    /**
     * Find all appointments for a given patient on a specific date.
     *
     * <p>Generated query: {@code SELECT * FROM appointments WHERE patient_id = ? AND appointment_date = ?}</p>
     *
     * @param patientId the patient's ID
     * @param appointmentDate the appointment date
     * @return list of appointments for that patient on that date
     */
    List<Appointment> findByPatientIdAndAppointmentDate(Long patientId, LocalDate appointmentDate);

    /**
     * Find the doctor's existing appointments that clash with a proposed time window.
     *
     * <p>Two appointments clash when they are for the same doctor on the same date and
     * one starts before the other ends:</p>
     *
     * <pre>
     * existing.start_time &lt; proposed.end_time
     * AND existing.end_time &gt; proposed.start_time
     * </pre>
     *
     * <p>Back-to-back bookings are therefore allowed (10:00-11:00 and 11:00-12:00 do not
     * clash) while any real overlap is returned.</p>
     *
     * <p>Appointments whose status equals {@code excludedStatus} are ignored, so cancelled
     * appointments release their slot. The status is a typed parameter (not a string
     * literal) so JPQL compares it as an {@link AppointmentStatus}.</p>
     *
     * @param doctorId the doctor's ID
     * @param appointmentDate the date to check
     * @param startTime the proposed start time
     * @param endTime the proposed end time
     * @param excludedStatus status that does not block a slot (CANCELLED)
     * @return appointments that clash with the proposed window (empty when the slot is free)
     */
    @Query("SELECT a FROM Appointment a WHERE a.doctor.id = :doctorId " +
           "AND a.appointmentDate = :appointmentDate " +
           "AND a.status <> :excludedStatus " +
           "AND a.startTime < :endTime " +
           "AND a.endTime > :startTime")
    List<Appointment> findConflictingAppointments(
            @Param("doctorId") Long doctorId,
            @Param("appointmentDate") LocalDate appointmentDate,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime,
            @Param("excludedStatus") AppointmentStatus excludedStatus
    );

    /**
     * Lock one appointment row with {@code SELECT ... FOR UPDATE}.
     *
     * <p>Used by appointment status changes (for example cancellation) so two concurrent
     * requests cannot both read the same status and then write conflicting history rows.
     * The lock is held until the surrounding transaction commits or rolls back, so this
     * method must be called from inside a {@code @Transactional} service method.</p>
     *
     * @param id the appointment's ID
     * @return the locked appointment, or empty when no such appointment exists
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Appointment a WHERE a.id = :id")
    Optional<Appointment> findByIdForUpdate(@Param("id") Long id);

    /**
     * Read one appointment together with its patient and doctor in a single query.
     *
     * <p>{@code JOIN FETCH} is used because both relationships are mapped
     * {@code FetchType.LAZY} and Open Session In View is disabled
     * ({@code spring.jpa.open-in-view=false}). Fetching them — and their user accounts,
     * whose names the response carries — here means the caller can safely read the
     * participants after the transaction has ended, which is what the REST response
     * mapper needs.</p>
     *
     * @param id the appointment's ID
     * @return the appointment with patient, doctor and their accounts initialized, or empty when not found
     */
    @Query("SELECT a FROM Appointment a " +
           "JOIN FETCH a.patient p JOIN FETCH p.user " +
           "JOIN FETCH a.doctor d JOIN FETCH d.user " +
           "WHERE a.id = :id")
    Optional<Appointment> findByIdWithPatientAndDoctor(@Param("id") Long id);

    /**
     * Find one appointment by ID only when it belongs to the given patient, fetching
     * patient and doctor in the same query (Open Session In View is disabled).
     *
     * <p>Ownership filter lives in the WHERE clause so an unauthorized read is
     * indistinguishable from a missing row at the query level; the service decides
     * which HTTP status (404 vs 403) each case maps to.</p>
     *
     * @param id        the appointment's ID
     * @param patientId the patient's ID (the owning side, not the user's ID)
     * @return the appointment with patient, doctor and their accounts initialized, or empty when not owned
     */
    @Query("SELECT a FROM Appointment a " +
           "JOIN FETCH a.patient p JOIN FETCH p.user " +
           "JOIN FETCH a.doctor d JOIN FETCH d.user " +
           "WHERE a.id = :id AND p.id = :patientId")
    Optional<Appointment> findByIdAndPatientIdWithParticipants(
            @Param("id") Long id, @Param("patientId") Long patientId);

    /**
     * Find one appointment by ID only when it is assigned to the given doctor, fetching
     * patient and doctor in the same query.
     *
     * @param id       the appointment's ID
     * @param doctorId the doctor's ID (the assigned side, not the user's ID)
     * @return the appointment with patient, doctor and their accounts initialized, or empty when not assigned
     */
    @Query("SELECT a FROM Appointment a " +
           "JOIN FETCH a.patient p JOIN FETCH p.user " +
           "JOIN FETCH a.doctor d JOIN FETCH d.user " +
           "WHERE a.id = :id AND d.id = :doctorId")
    Optional<Appointment> findByIdAndDoctorIdWithParticipants(
            @Param("id") Long id, @Param("doctorId") Long doctorId);

    /**
     * All appointments of one patient (via the patient's user account), newest first.
     *
     * <p>Traverses {@code Appointment → Patient → User}, so the lookup key is the
     * authenticated user's ID — the value the JWT carries — not the patient row ID.
     * {@code JOIN FETCH} keeps participant IDs readable after the transaction ends.</p>
     *
     * @param userId the owning patient's user ID
     * @return the patient's appointments, newest first
     */
    @Query("SELECT a FROM Appointment a " +
           "JOIN FETCH a.patient p JOIN FETCH p.user " +
           "JOIN FETCH a.doctor d JOIN FETCH d.user " +
           "WHERE p.user.id = :userId ORDER BY a.appointmentDate DESC, a.startTime DESC")
    List<Appointment> findAllByPatientUserId(@Param("userId") Long userId);

    /**
     * All appointments assigned to one doctor (via the doctor's user account), newest first.
     *
     * @param userId the assigned doctor's user ID
     * @return the doctor's appointments, newest first
     */
    @Query("SELECT a FROM Appointment a " +
           "JOIN FETCH a.patient p JOIN FETCH p.user " +
           "JOIN FETCH a.doctor d JOIN FETCH d.user " +
           "WHERE d.user.id = :userId ORDER BY a.appointmentDate DESC, a.startTime DESC")
    List<Appointment> findAllByDoctorUserId(@Param("userId") Long userId);

    /**
     * ADMIN clinic-wide appointment list with every filter optional.
     *
     * <p>All four conditions use the {@code (:param IS NULL OR ...) } idiom: a {@code null}
     * parameter means "do not filter on this", so one query serves every filter combination
     * (no filter, one filter, or any mix). A null never matches a real row, so a null
     * parameter is equivalent to omitting the condition.</p>
     *
     * <p>{@code JOIN FETCH} initializes every association the admin response needs — the
     * patient and its account, the doctor, the doctor's account and specialty — in one
     * round trip. This matters because Open Session In View is disabled
     * ({@code spring.jpa.open-in-view=false}): without the fetch joins, reading names or
     * the specialty while mapping the DTOs would fail after the transaction ends, and with
     * lazy access inside the transaction each row would trigger extra queries (N+1).</p>
     *
     * <p>Newest visits first, ties broken by start time and then ID so the order is stable.</p>
     *
     * @param appointmentDate exact clinic date to show, or {@code null} for any date
     * @param status          lifecycle status to show, or {@code null} for any status
     * @param doctorId        doctor to show, or {@code null} for every doctor
     * @param patientId       patient to show, or {@code null} for every patient
     * @return the matching appointments with all participants initialized
     */
    @Query("SELECT a FROM Appointment a " +
           "JOIN FETCH a.patient p JOIN FETCH p.user " +
           "JOIN FETCH a.doctor d JOIN FETCH d.user JOIN FETCH d.specialty " +
           "WHERE (:appointmentDate IS NULL OR a.appointmentDate = :appointmentDate) " +
           "AND (:status IS NULL OR a.status = :status) " +
           "AND (:doctorId IS NULL OR d.id = :doctorId) " +
           "AND (:patientId IS NULL OR p.id = :patientId) " +
           "ORDER BY a.appointmentDate DESC, a.startTime DESC, a.id DESC")
    List<Appointment> findAdminAppointments(
            @Param("appointmentDate") LocalDate appointmentDate,
            @Param("status") AppointmentStatus status,
            @Param("doctorId") Long doctorId,
            @Param("patientId") Long patientId);

    /**
     * Read one appointment for the ADMIN detail view together with everything the admin
     * response needs: patient + account, doctor + account + specialty.
     *
     * <p>Same fetch-join rationale as {@link #findAdminAppointments}: one query, safe to
     * read after the transaction, no lazy-loading failure with OSIV disabled.</p>
     *
     * @param id the appointment's ID
     * @return the appointment with all participants initialized, or empty when not found
     */
    @Query("SELECT a FROM Appointment a " +
           "JOIN FETCH a.patient p JOIN FETCH p.user " +
           "JOIN FETCH a.doctor d JOIN FETCH d.user JOIN FETCH d.specialty " +
           "WHERE a.id = :id")
    Optional<Appointment> findByIdForAdmin(@Param("id") Long id);

    /**
     * Count how many appointments carry each lifecycle status, in one grouped query.
     *
     * <p>Returns {@link AppointmentStatusCount} projection rows (aliased JPQL aggregate)
     * instead of five separate {@code countByStatus} calls, so the dashboard overview costs
     * one round trip, not five.</p>
     *
     * @return one row per status that currently exists in the table (statuses with zero
     *         appointments are absent — the service fills them in)
     */
    @Query("SELECT a.status AS status, COUNT(a) AS total FROM Appointment a GROUP BY a.status")
    List<AppointmentStatusCount> countGroupedByStatus();

    /**
     * Count the appointments booked for one clinic date (the dashboard's "today" figure).
     *
     * <p>A derived query is enough here: only the count is needed, no participant data.</p>
     *
     * @param appointmentDate the clinic date to count
     * @return number of appointments on that date, whatever their status
     */
    long countByAppointmentDate(LocalDate appointmentDate);
}
