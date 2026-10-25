package com.medbook.bootstrap;

import com.medbook.entity.Role;
import com.medbook.entity.User;
import com.medbook.repository.RoleRepository;
import com.medbook.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link BootstrapAccountService} against the real database.
 *
 * <p>{@code @Transactional} keeps every account created here rolled back, so the suite
 * leaves no residue. The tests pin the two security properties that matter: the password
 * is only ever stored as a BCrypt hash, and an account receives exactly the role it was
 * asked for and nothing else.</p>
 */
@SpringBootTest
@Transactional
class BootstrapAccountServiceTest {

    @Autowired
    private BootstrapAccountService accountService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + System.nanoTime() + "@medbook.test";
    }

    @Test
    void createsAdminWithHashedPasswordAndOnlyAdminRole() {
        String email = uniqueEmail("bootstrap-admin");

        User created = accountService.ensureUser(email, "SecretPass1", "Boot", "Strap",
                "+10000000010", BootstrapAccountService.ROLE_ADMIN).orElseThrow();

        // The stored hash is real BCrypt and not the plaintext.
        assertThat(created.getPasswordHash()).startsWith("$2");
        assertThat(created.getPasswordHash()).isNotEqualTo("SecretPass1");
        assertThat(passwordEncoder.matches("SecretPass1", created.getPasswordHash())).isTrue();

        // Exactly the ADMIN role — the bootstrap can never mint something broader.
        assertThat(created.getRoles()).extracting(Role::getName).containsExactly("ADMIN");
        assertThat(created.hasRole("DOCTOR")).isFalse();
        assertThat(created.hasRole("PATIENT")).isFalse();
    }

    @Test
    void creatingADoctorAccountAssignsOnlyDoctorRole() {
        String email = uniqueEmail("bootstrap-doc");

        User created = accountService.ensureUser(email, "DoctorPass1", "Doc", "Tor",
                "+10000000011", "DOCTOR").orElseThrow();

        assertThat(created.getRoles()).extracting(Role::getName).containsExactly("DOCTOR");
        assertThat(created.hasRole("ADMIN")).isFalse();
    }

    @Test
    void ensureUserIsIdempotentByEmail() {
        String email = uniqueEmail("bootstrap-idem");

        User first = accountService.ensureUser(email, "SecretPass1", "Idem", "Potent",
                "+10000000012", BootstrapAccountService.ROLE_ADMIN).orElseThrow();
        User second = accountService.ensureUser(email, "AnotherPass1", "Idem", "Potent",
                "+10000000012", BootstrapAccountService.ROLE_ADMIN).orElseThrow();

        // Same existing account returned; the second password is never applied.
        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(passwordEncoder.matches("SecretPass1", second.getPasswordHash())).isTrue();
    }

    @Test
    void adminExistsReportsADatabaseBackedAdmin() {
        accountService.ensureUser(uniqueEmail("bootstrap-exists"), "SecretPass1", "Exists", "Check",
                "+10000000013", BootstrapAccountService.ROLE_ADMIN);

        // The inserted admin is visible inside this transaction.
        assertThat(accountService.adminExists()).isTrue();
    }

    @Test
    void anExistingAdminIsReturnedUntouchedByALaterBootstrapCall() {
        // Pin the acceptance criteria: restart with the same configured credentials
        // must not duplicate the account, must not overwrite the password, and must
        // not duplicate the role — the existing row comes back exactly as it was.
        String email = uniqueEmail("bootstrap-nooverwrite");
        User original = accountService.ensureUser(email, "OriginalPass1", "Origin", "Al",
                "+10000000014", BootstrapAccountService.ROLE_ADMIN).orElseThrow();
        String originalHash = original.getPasswordHash();
        int originalRoleCount = original.getRoles().size();

        User again = accountService.ensureUser(email, "DifferentPass9", "Different", "Name",
                "+19999999999", BootstrapAccountService.ROLE_ADMIN).orElseThrow();

        assertThat(again.getId()).isEqualTo(original.getId());
        assertThat(again.getPasswordHash()).isEqualTo(originalHash);
        assertThat(passwordEncoder.matches("OriginalPass1", again.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("DifferentPass9", again.getPasswordHash())).isFalse();
        assertThat(again.getRoles()).extracting(Role::getName).containsExactly("ADMIN");
        assertThat(again.getRoles()).hasSize(originalRoleCount);
        assertThat(again.getFirstName()).isEqualTo("Origin");
        assertThat(again.getLastName()).isEqualTo("Al");
    }

    @Test
    void emailLookupIsCaseInsensitiveForTheConfiguredAccount() {
        // ensureUser normalizes emails to lower case, so a differently-cased
        // configuration (or data-entry) still resolves to the same single account.
        String email = uniqueEmail("bootstrap-case");
        User created = accountService.ensureUser(email, "SecretPass1", "Case", "Check",
                "+10000000015", BootstrapAccountService.ROLE_ADMIN).orElseThrow();

        assertThat(created.getEmail()).isEqualTo(email.toLowerCase());

        User sameAccount = accountService.ensureUser(email.toUpperCase(), "SecretPass1", "Case", "Check",
                "+10000000015", BootstrapAccountService.ROLE_ADMIN).orElseThrow();
        assertThat(sameAccount.getId()).isEqualTo(created.getId());
    }

    @Test
    void rolesTheTestsRelyOnAreSeeded() {
        assertThat(roleRepository.findByName("ADMIN")).isPresent();
        assertThat(roleRepository.findByName("DOCTOR")).isPresent();
        assertThat(roleRepository.findByName("PATIENT")).isPresent();
        assertThat(userRepository).isNotNull();
    }
}
