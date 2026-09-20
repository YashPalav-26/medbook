package com.medbook.repository;

import com.medbook.entity.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link RoleRepository} against the real MySQL schema.
 *
 * <p>These tests read the roles seeded by the Flyway V1 migration. They are
 * marked {@link Transactional} with the default rollback so that no test leaves
 * data behind in the shared development database.</p>
 */
@SpringBootTest
@Transactional
class RoleRepositoryTest {

    @Autowired
    private RoleRepository roleRepository;

    @Test
    void findAllReturnsSeededRoles() {
        List<Role> roles = roleRepository.findAll();

        assertThat(roles).hasSize(3);
        assertThat(roles).extracting(Role::getName)
                .containsExactlyInAnyOrder("ADMIN", "DOCTOR", "PATIENT");
    }

    @Test
    void findByNameReturnsSeededAdminRole() {
        var maybeAdmin = roleRepository.findByName("ADMIN");

        assertThat(maybeAdmin).isPresent();

        Role admin = maybeAdmin.get();
        assertThat(admin.getId()).isNotNull();
        assertThat(admin.getName()).isEqualTo("ADMIN");
        assertThat(admin.getDescription()).isNotBlank();
    }

    @Test
    void findByNameReturnsEmptyForUnknownRole() {
        assertThat(roleRepository.findByName("SUPERUSER")).isEmpty();
    }

    @Test
    void entityIsMappedToExistingSchema() {
        // Hibernate validated the mapping at startup (ddl-auto=validate),
        // so simply loading a row exercises every column mapping.
        Role admin = roleRepository.findByName("ADMIN").orElseThrow();

        assertThat(admin.getId()).isNotNull();
        assertThat(admin.getName()).isEqualTo("ADMIN");
        assertThat(admin.getDescription()).isNotBlank();
        assertThat(admin.getUsers()).isNotNull();
    }
}
