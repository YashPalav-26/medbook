package com.medbook.repository;

import com.medbook.entity.Appointment;
import com.medbook.entity.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

/**
 * Spring Data JPA repository for {@link Appointment} entities.
 *
 * <p>Provides derived query methods for common appointment lookups,
 * following Spring Data JPA naming conventions.</p>
 */
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
}
