package com.medbook.service;

import com.medbook.dto.AuthResponse;
import com.medbook.dto.LoginRequest;
import com.medbook.dto.RegisterRequest;
import com.medbook.entity.Patient;
import com.medbook.entity.Role;
import com.medbook.entity.User;
import com.medbook.repository.PatientRepository;
import com.medbook.repository.RoleRepository;
import com.medbook.repository.UserRepository;
import com.medbook.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;





















@Service
public class AuthService {





    private static final String DEFAULT_ROLE = "PATIENT";

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;





    private volatile String dummyHash;

    public AuthService(
            UserRepository userRepository,
            PatientRepository patientRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.patientRepository = patientRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }








    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalize(request.email());

        if (userRepository.findByEmail(email).isPresent()) {
            throw new EmailAlreadyRegisteredException();
        }

        Role patientRole = roleRepository.findByName(DEFAULT_ROLE)
                .orElseThrow(() -> new IllegalStateException("PATIENT role is not seeded"));



        String passwordHash = passwordEncoder.encode(request.password());

        User user = new User(
                email,
                passwordHash,
                request.firstName().trim(),
                request.lastName().trim(),
                request.phoneNumber().trim()
        );
        user.addRole(patientRole);
        User savedUser = userRepository.save(user);

        patientRepository.save(new Patient(savedUser, request.dateOfBirth()));

        return buildAuthResponse(savedUser);
    }










    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = normalize(request.email());
        Optional<User> maybeUser = userRepository.findByEmail(email);

        if (maybeUser.isEmpty()) {


            passwordEncoder.matches(request.password(), dummyHash());
            throw new InvalidCredentialsException();
        }

        User user = maybeUser.get();

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
            userRepository.save(user);
            throw new InvalidCredentialsException();
        }

        if (user.isLocked()) {
            throw new AccountLockedException();
        }

        if (!user.isActive()) {
            throw new AccountInactiveException();
        }

        user.setFailedLoginAttempts(0);
        user.setLastLoginAt(LocalDateTime.now(ZoneOffset.UTC));
        userRepository.save(user);

        return buildAuthResponse(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        String token = jwtService.generateToken(user);

        List<String> roles = user.getRoles().stream()
                .map(role -> "ROLE_" + role.getName())
                .sorted()
                .toList();

        return new AuthResponse(
                token,
                "Bearer",
                jwtService.getExpirationMinutes(),
                new AuthResponse.UserSummary(
                        user.getId(),
                        user.getEmail(),
                        user.getFirstName(),
                        user.getLastName(),
                        roles
                )
        );
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String dummyHash() {
        if (dummyHash == null) {
            dummyHash = passwordEncoder.encode("dummy-password-never-matches-a-real-user");
        }
        return dummyHash;
    }
}
