package com.medbook.repository;

import com.medbook.entity.Role;
import com.medbook.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link UserRepository} against the real MySQL schema.
 *
 * <p>No application users exist in the database yet, so these tests focus on
 * demonstrating repository behaviour without inventing fake application users:
 * {@code findAll()} proves the table is reachable and mapped, and
 * {@code findByEmail(...)} proves the derived query method is generated and
 * correctly returns an empty result.</p>
 *
 * <p>All write-capable tests are wrapped in a {@link Transactional} context that
 * rolls back, so nothing persists to the shared development database.</p>
 */
@SpringBootTest
@Transactional
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void findAllExecutesAgainstRealSchema() {
        // No users have been created yet; the point is that the query runs
        // against the mapped `users` table without error.
        assertThat(userRepository.findAll()).isNotNull();
    }

    @Test
    void findByEmailReturnsEmptyWhenNoUserExists() {
        assertThat(userRepository.findByEmail("nobody@medbook.example")).isEmpty();
    }

    @Test
    void savedUserIsRetrievableByEmail() {
        // Demonstrates the full save/read round trip, including the
        // many-to-many join to the seeded PATIENT role. Rolled back afterwards.
        Role patient = roleRepository.findByName("PATIENT").orElseThrow();

        User user = new User("test.user@medbook.example", "hashed-not-real",
                "Test", "User", "+919999999999");
        user.addRole(patient);

        User saved = userRepository.save(user);

        assertThat(saved.getId()).isNotNull();

        var reloaded = userRepository.findByEmail("test.user@medbook.example");

        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getFirstName()).isEqualTo("Test");
        assertThat(reloaded.get().getRoles())
                .extracting(Role::getName)
                .containsExactly("PATIENT");
    }

    @Test
    void savedUserIsAssignedTimestampsByDatabaseDefaults() {
        // created_at/updated_at are NOT NULL with DB defaults and are marked
        // insertable=false on the entity, so Hibernate leaves them out of the
        // INSERT and MySQL fills them. A flush + refresh proves the DB-generated
        // values round-trip back into the entity.
        User user = new User("timestamp.user@medbook.example", "hashed-not-real",
                "Timestamp", "User", "+918888888888");

        User saved = userRepository.save(user);
        userRepository.flush();
        entityManager.refresh(saved);

        assertThat(saved.getCreatedAt()).as("created_at populated by DB default").isNotNull();
        assertThat(saved.getUpdatedAt()).as("updated_at populated by DB default").isNotNull();
    }
}
