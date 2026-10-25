package com.medbook.bootstrap;

import com.medbook.entity.Role;
import com.medbook.entity.User;
import com.medbook.repository.RoleRepository;
import com.medbook.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

/**
 * Creates login accounts for the development/bootstrap flows without going through the
 * public registration endpoint.
 *
 * <p>This is the single, controlled place where an account can be granted a role other
 * than PATIENT. It is never reachable from HTTP: it is invoked only by the application
 * startup runners ({@link AdminBootstrapRunner}, {@link DemoDataSeeder}). The public
 * {@code POST /api/v1/auth/register} endpoint remains patient-only and is untouched.</p>
 *
 * <p>Safety properties:</p>
 * <ul>
 * <li>the password is hashed with the application's existing {@link PasswordEncoder}
 *     (BCrypt) — the plaintext is never stored and never logged;</li>
 * <li>the account is idempotent by email: calling {@link #ensureUser} twice for the same
 *     email creates exactly one account;</li>
 * <li>only the single role named by the caller is granted.</li>
 * </ul>
 */
@Service
public class BootstrapAccountService {

    /** Role name for administrators, exactly as seeded in the {@code roles} table. */
    public static final String ROLE_ADMIN = "ADMIN";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public BootstrapAccountService(UserRepository userRepository,
                                   RoleRepository roleRepository,
                                   PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** Whether at least one administrator account already exists. */
    @Transactional(readOnly = true)
    public boolean adminExists() {
        return userRepository.existsByRoles_Name(ROLE_ADMIN);
    }

    /**
     * Find or create an account for the given email with exactly the given role.
     *
     * @param email    sign-in email (stored lower-cased); blank e-mail is rejected by the caller
     * @param password plaintext password, immediately BCrypt-hashed (never persisted raw)
     * @param firstName given name
     * @param lastName  family name
     * @param phoneNumber contact number
     * @param roleName  the single role to grant (for example {@code "ADMIN"} or {@code "DOCTOR"})
     * @return the existing account when the email is already registered, otherwise the new one
     * @throws IllegalStateException when the named role is not seeded
     */
    @Transactional
    public Optional<User> ensureUser(String email,
                                     String password,
                                     String firstName,
                                     String lastName,
                                     String phoneNumber,
                                     String roleName) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        Optional<User> existing = userRepository.findByEmail(normalizedEmail);
        if (existing.isPresent()) {
            return existing;
        }

        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException(roleName + " role is not seeded"));

        User user = new User(
                normalizedEmail,
                passwordEncoder.encode(password),
                firstName.trim(),
                lastName.trim(),
                phoneNumber.trim()
        );
        user.addRole(role);
        return Optional.of(userRepository.save(user));
    }
}
