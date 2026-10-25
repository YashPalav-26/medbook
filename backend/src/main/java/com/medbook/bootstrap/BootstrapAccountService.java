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



















@Service
public class BootstrapAccountService {


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


    @Transactional(readOnly = true)
    public boolean adminExists() {
        return userRepository.existsByRoles_Name(ROLE_ADMIN);
    }













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
