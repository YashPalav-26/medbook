package com.medbook.repository;

import com.medbook.entity.Specialty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
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

    /**
     * All active specialties, ordered by name.
     *
     * <p>Powers {@code GET /api/v1/specialties} — the booking UI's specialty dropdown.
     * Inactive rows (retired specialties) never appear in the list.</p>
     *
     * @return active specialties sorted alphabetically
     */
    List<Specialty> findByActiveTrueOrderByNameAsc();

    /**
     * Case-insensitive uniqueness probe for specialty names.
     *
     * <p>Used by the admin service to reject duplicates with a friendly 409 before
     * the database's UNIQUE constraint turns the same situation into a raw 500.
     * Case-insensitive so "Cardiology" and "cardiology" are treated as the same name.</p>
     */
    boolean existsByNameIgnoreCase(String name);

    /**
     * Case-insensitive uniqueness probe excluding one row — the update variant:
     * "does another specialty (not {@code id}) already use this name?".
     */
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    /**
     * Every specialty (active and inactive), ordered by name — the admin list.
     * The public discovery API stays active-only; admins manage both states.
     */
    List<Specialty> findAllByOrderByNameAsc();
}
