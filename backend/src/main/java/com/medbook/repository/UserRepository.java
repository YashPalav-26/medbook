package com.medbook.repository;

import com.medbook.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link User}.
 *
 * <p>No service or controller uses this yet; it exists so that user lookup by
 * email (the natural key used at sign-in) can be exercised.</p>
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Derived query method: Spring Data JPA generates the SQL
     * {@code SELECT ... FROM users WHERE email = ?} at startup.
     */
    Optional<User> findByEmail(String email);

    /**
     * Case-insensitive email existence probe (emails are stored lower-cased).
     * Used by doctor creation to reject a duplicate account with a clean 409
     * instead of a database constraint violation.
     */
    boolean existsByEmailIgnoreCase(String email);

    /**
     * Does any user hold the given role (for example {@code "ADMIN"})?
     *
     * <p>Derived query traversing the {@code user_roles} join to {@code roles.name}.
     * Used by the first-admin bootstrap to answer "does an administrator already exist?"
     * without loading every user.</p>
     */
    boolean existsByRoles_Name(String name);

    /**
     * Every user holding the given role (for example {@code "ADMIN"}), oldest first.
     *
     * <p>Derived query traversing the {@code user_roles} join; the entity graph eagerly
     * loads each user's full role set so DTO mapping can read roles outside the
     * transaction (the many-to-many is lazy by default). Used by the admin-management
     * list endpoint.</p>
     */
    @EntityGraph(attributePaths = "roles")
    List<User> findByRoles_NameOrderByIdAsc(String name);
}
