package com.medbook.repository;

import com.medbook.entity.Role;
import com.medbook.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;













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


        assertThat(userRepository.findAll()).isNotNull();
    }

    @Test
    void findByEmailReturnsEmptyWhenNoUserExists() {
        assertThat(userRepository.findByEmail("nobody@medbook.example")).isEmpty();
    }

    @Test
    void savedUserIsRetrievableByEmail() {


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




        User user = new User("timestamp.user@medbook.example", "hashed-not-real",
                "Timestamp", "User", "+918888888888");

        User saved = userRepository.save(user);
        userRepository.flush();
        entityManager.refresh(saved);

        assertThat(saved.getCreatedAt()).as("created_at populated by DB default").isNotNull();
        assertThat(saved.getUpdatedAt()).as("updated_at populated by DB default").isNotNull();
    }
}
