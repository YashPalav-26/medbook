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
}
