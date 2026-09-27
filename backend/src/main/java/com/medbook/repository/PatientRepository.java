package com.medbook.repository;

import com.medbook.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Patient}.
 *
 * <p>Provides queries for patient profiles. Patients are individuals registered
 * to use the appointment system.</p>
 */
@Repository
public interface PatientRepository extends JpaRepository<Patient, Long> {

    /**
     * Derived query method: Spring Data JPA generates SQL
     * {@code SELECT ... FROM patients WHERE user_id = ?}
     *
     * <p>user_id is UNIQUE in the schema, so this returns at most one result.
     * Used to look up a patient by the user account they are linked to.</p>
     */
    Optional<Patient> findByUserId(Long userId);

    /**
     * Derived query method: Spring Data JPA generates SQL
     * {@code SELECT ... FROM patients WHERE is_active = true}
     *
     * <p>Returns all active patients. Useful for queries that should exclude
     * soft-deleted patient records.</p>
     */
    java.util.List<Patient> findByActiveTrue();

    /**
     * All patients (active and inactive) with the linked {@code user} fetched — the
     * admin list. The fetch join is needed because Open Session In View is disabled
     * and the admin response reads the user's account fields after the transaction.
     */
    @Query("SELECT p FROM Patient p JOIN FETCH p.user ORDER BY p.id ASC")
    java.util.List<Patient> findAllWithUser();
}
