package com.medbook.repository;

import com.medbook.entity.AppointmentStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link AppointmentStatusHistory} entities.
 *
 * <p>Provides derived query methods for accessing appointment status change history,
 * following Spring Data JPA naming conventions.</p>
 */
public interface AppointmentStatusHistoryRepository extends JpaRepository<AppointmentStatusHistory, Long> {

    /**
     * Find all status change records for a given appointment.
     *
     * <p>Generated query: {@code SELECT * FROM appointment_status_history WHERE appointment_id = ?}</p>
     *
     * @param appointmentId the appointment's ID
     * @return list of status history records for that appointment, ordered by changed_at
     */
    List<AppointmentStatusHistory> findByAppointmentIdOrderByChangedAtAsc(Long appointmentId);

    /**
     * Find all status change records for a given appointment.
     *
     * <p>Generated query: {@code SELECT * FROM appointment_status_history WHERE appointment_id = ?}</p>
     *
     * @param appointmentId the appointment's ID
     * @return list of status history records for that appointment
     */
    List<AppointmentStatusHistory> findByAppointmentId(Long appointmentId);
}
