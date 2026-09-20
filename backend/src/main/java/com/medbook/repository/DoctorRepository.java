package com.medbook.repository;

import com.medbook.entity.Doctor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Doctor}.
 *
 * <p>Provides queries for doctor profiles. Demonstrates nested property
 * traversal in derived query methods: {@code findBySpecialtyName(...)}
 * navigates {@code Doctor.specialty.name} without requiring manual JPQL.</p>
 */
@Repository
public interface DoctorRepository extends JpaRepository<Doctor, Long> {

    /**
     * Derived query method: Spring Data JPA generates SQL
     * {@code SELECT ... FROM doctors WHERE medical_license_number = ?}
     *
     * <p>Medical license numbers are unique in the schema, so this returns
     * at most one result.</p>
     */
    Optional<Doctor> findByMedicalLicenseNumber(String medicalLicenseNumber);

    /**
     * Derived query method with nested property traversal.
     * Spring Data JPA generates SQL
     * {@code SELECT ... FROM doctors d
     *        JOIN specialties s ON d.specialty_id = s.id
     *        WHERE s.name = ?}
     *
     * <p>Returns all doctors whose specialty name matches (e.g., all Cardiologists).
     * The method name {@code findBySpecialtyName} instructs Spring Data to:
     * 1. Navigate from Doctor.specialty (the relationship field)
     * 2. To specialty.name (the nested property to filter on)</p>
     */
    List<Doctor> findBySpecialtyName(String specialtyName);

    /**
     * Derived query method: Spring Data JPA generates SQL
     * {@code SELECT ... FROM doctors WHERE is_active = true}
     *
     * <p>Returns all active doctors. The {@code True} suffix in the method name
     * tells Spring Data to filter by {@code active = true}.</p>
     */
    List<Doctor> findByActiveTrue();

    /**
     * Derived query method: Spring Data JPA generates SQL
     * {@code SELECT ... FROM doctors WHERE user_id = ?}
     *
     * <p>user_id is UNIQUE in the schema, so this returns at most one result.
     * Used to look up a doctor by the user account they are linked to.</p>
     */
    Optional<Doctor> findByUserId(Long userId);
}
