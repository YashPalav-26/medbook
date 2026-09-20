package com.medbook.repository;

import com.medbook.entity.Specialty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Specialty}.
 *
 * <p>Provides queries for medical specialties. Specialties are reference data
 * managed administratively, so this repository primarily supports reads and
 * lookups by name (the natural key used in dropdowns and displays).</p>
 */
@Repository
public interface SpecialtyRepository extends JpaRepository<Specialty, Long> {

    /**
     * Derived query method: Spring Data JPA generates SQL
     * {@code SELECT ... FROM specialties WHERE name = ?}
     *
     * <p>Used to look up a specialty by its exact name (case-sensitive).</p>
     */
    Optional<Specialty> findByName(String name);

    /**
     * Derived query method with IgnoreCase modifier.
     * Spring Data JPA generates SQL
     * {@code SELECT ... FROM specialties WHERE LOWER(name) = LOWER(?)}
     *
     * <p>Useful for UI scenarios where the user types "Cardiology" or
     * "cardiology" and the backend should find the canonical row regardless
     * of case.</p>
     */
    Optional<Specialty> findByNameIgnoreCase(String name);
}
