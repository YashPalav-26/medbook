package com.medbook.service;

import com.medbook.dto.AdminCreateRequest;
import com.medbook.entity.Role;
import com.medbook.entity.User;
import com.medbook.repository.RoleRepository;
import com.medbook.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * ADMIN management of administrator accounts (list / create).
 *
 * <p>This is the second, UI-driven counterpart of the startup
 * {@link com.medbook.bootstrap.AdminBootstrapRunner}: where the runner provisions the
 * configured initial administrator from application properties, this service lets an
 * already-authenticated administrator mint further administrators through the Admin
 * Management screen. Both funnel account creation through the same invariants:</p>
 *
 * <ul>
 * <li>the password is hashed with the application's existing {@link PasswordEncoder}
 *     (BCrypt) — the plaintext is never stored and never logged;</li>
 * <li>the ADMIN role is assigned server-side; the request DTO carries no role field,
 *     so a client can never ask for anything else;</li>
 * <li>duplicate emails are rejected with a clean {@link EmailAlreadyRegisteredException}
 *     (409) instead of a database constraint violation;</li>
 * <li>responses are DTOs ({@link com.medbook.dto.AdminAccountResponse}) so no password
 *     hash or other security internal can ever be serialized.</li>
 * </ul>
 *
 * <p>Authorization is URL-level: {@code /api/v1/admin/**} requires ROLE_ADMIN in
 * {@link com.medbook.security.SecurityConfig}, so anonymous callers get 401 and
 * authenticated non-admins 403 before this service is ever reached. Deactivation of
 * administrators (with last-active-admin lockout protection) is a documented future
 * feature — this service intentionally offers only list and create.</p>
 */
@Service
public class AdminAdminService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminAdminService(UserRepository userRepository,
                             RoleRepository roleRepository,
                             PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Every administrator account, oldest first, including inactive ones so the
     * management screen shows the full picture.
     */
    @Transactional(readOnly = true)
    public List<User> listAll() {
        return userRepository.findByRoles_NameOrderByIdAsc("ADMIN");
    }

    /**
     * Create an administrator together with their login account and ADMIN role.
     *
     * @throws EmailAlreadyRegisteredException when the email is taken (409)
     * @throws IllegalStateException           when the ADMIN role is not seeded
     */
    @Transactional
    public User create(AdminCreateRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new EmailAlreadyRegisteredException();
        }

        Role adminRole = roleRepository.findByName("ADMIN")
                .orElseThrow(() -> new IllegalStateException("ADMIN role not seeded"));

        // Only the BCrypt hash of the password is ever stored.
        User user = new User(normalizedEmail, passwordEncoder.encode(request.password()),
                request.firstName().trim(), request.lastName().trim(), request.phoneNumber().trim());
        user.addRole(adminRole);
        return userRepository.save(user);
    }
}
