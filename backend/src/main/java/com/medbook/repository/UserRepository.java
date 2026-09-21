package com.medbook.repository;

import com.medbook.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}
