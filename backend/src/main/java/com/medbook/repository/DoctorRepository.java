package com.medbook.repository;

import com.medbook.entity.Doctor;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /** Uniqueness probe for license numbers — clean 409 instead of a constraint 500. */
    boolean existsByMedicalLicenseNumber(String medicalLicenseNumber);

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

    /**
     * All active doctors with their {@code user} and {@code specialty} fetched in one query.
     *
     * <p>Fetch joins are required because Open Session In View is disabled: the list is
     * mapped to {@code DoctorSummaryResponse} outside any transaction, which touches
     * both associations. Ordering by id keeps API output stable between calls.</p>
     *
     * @return active doctors, user + specialty initialized, ordered by id
     */
    @Query("SELECT d FROM Doctor d JOIN FETCH d.user JOIN FETCH d.specialty "
            + "WHERE d.active = true ORDER BY d.id ASC")
    List<Doctor> findAllByActiveTrueWithUserAndSpecialty();

    /**
     * Active doctors of one specialty, with {@code user} and {@code specialty} fetched.
     *
     * <p>Same fetch-join reasoning as {@link #findAllByActiveTrueWithUserAndSpecialty()};
     * this variant powers the booking page's doctor dropdown after a specialty is chosen.</p>
     *
     * @param specialtyId the specialty to filter by
     * @return active doctors of that specialty, ordered by id
     */
    @Query("SELECT d FROM Doctor d JOIN FETCH d.user JOIN FETCH d.specialty "
            + "WHERE d.specialty.id = :specialtyId AND d.active = true ORDER BY d.id ASC")
    List<Doctor> findAllBySpecialtyIdAndActiveTrueWithUserAndSpecialty(@Param("specialtyId") Long specialtyId);

    /**
     * All doctors (active and inactive) with user + specialty fetched — the admin list.
     * Fetch joins are needed because Open Session In View is disabled and the admin
     * response reads both associations.
     */
    @Query("SELECT d FROM Doctor d JOIN FETCH d.user JOIN FETCH d.specialty ORDER BY d.id ASC")
    List<Doctor> findAllWithUserAndSpecialty();

    /**
     * One doctor by ID with user + specialty fetched — the admin detail/update path.
     */
    @Query("SELECT d FROM Doctor d JOIN FETCH d.user JOIN FETCH d.specialty WHERE d.id = :id")
    Optional<Doctor> findByIdWithUserAndSpecialty(@Param("id") Long id);

    /**
     * Uniqueness probe for license numbers excluding one row — the update variant.
     */
    boolean existsByMedicalLicenseNumberAndIdNot(String medicalLicenseNumber, Long id);

    /**
     * Lock doctor row with SELECT ... FOR UPDATE.
     * Use during appointment booking to prevent concurrent double-booking.
     *
     * <p>Pessimistic write lock held until transaction commits/rolls back.</p>
     *
     * @param id the doctor's ID
     * @return the locked doctor entity, or empty if not found
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Doctor d WHERE d.id = :id")
    Optional<Doctor> findByIdForUpdate(@Param("id") Long id);
}
