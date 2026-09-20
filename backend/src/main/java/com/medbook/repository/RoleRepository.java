package com.medbook.repository;

import com.medbook.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Role}.
 *
 * <p>No service or controller uses this yet; it exists so that role lookup
 * (especially the seeded ADMIN/DOCTOR/PATIENT rows) can be exercised.</p>
 */
@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    /**
     * Derived query method: Spring Data JPA generates the SQL
     * {@code SELECT ... FROM roles WHERE name = ?} at startup.
     */
    Optional<Role> findByName(String name);
}
