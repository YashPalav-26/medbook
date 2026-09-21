package com.medbook.repository;

import com.medbook.entity.DoctorAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link DoctorAvailability}.
 *
 * <p>Provides queries for doctor availability records. These records define
 * when doctors can accept appointments, not the appointments themselves.</p>
 */
@Repository
public interface DoctorAvailabilityRepository extends JpaRepository<DoctorAvailability, Long> {

    /**
     * Derived query method: Spring Data JPA generates SQL
     * {@code SELECT ... FROM doctor_availability WHERE doctor_id = ?}
     *
     * <p>Returns all availability records for a specific doctor, regardless
     * of whether they are active or recurring. Useful for showing a doctor's
     * complete availability schedule.</p>
     */
    List<DoctorAvailability> findByDoctorId(Long doctorId);

    /**
     * Derived query method: Spring Data JPA generates SQL
     * {@code SELECT ... FROM doctor_availability WHERE doctor_id = ? AND day_of_week = ?}
     *
     * <p>Returns all availability records for a specific doctor on a specific
     * day of the week. Useful for showing "what hours is this doctor available
     * on Mondays?"</p>
     */
    List<DoctorAvailability> findByDoctorIdAndDayOfWeek(Long doctorId, byte dayOfWeek);

    /**
     * One doctor's availability windows, ordered for stable API output.
     *
     * <p>Powers {@code GET /api/v1/doctors/{id}/availability}: day of week first,
     * then start time, so the weekly schedule reads like a calendar.</p>
     */
    List<DoctorAvailability> findByDoctorIdOrderByDayOfWeekAscStartTimeAsc(Long doctorId);
}
