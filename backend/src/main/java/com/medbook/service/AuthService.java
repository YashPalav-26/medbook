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

/**
 * Service for patient self-registration and sign-in.
 *
 * <h3>Registration</h3>
 * <p>Always creates a user with the PATIENT role plus the matching patient profile, so a
 * freshly registered account can immediately book appointments. The request has nowhere to
 * ask for ADMIN or DOCTOR — privilege escalation through the public endpoint is
 * structurally impossible.</p>
 *
 * <h3>Login</h3>
 * <p>Verifies email + BCrypt password. All wrong answers produce the same generic 401; the
 * specific lock/inactive messages are only revealed after the password matched (i.e. to
 * someone who already knows the credentials).</p>
 *
 * <h3>Failure tracking (schema columns only, no policy)</h3>
 * <p>{@code failed_login_attempts} is incremented on a wrong password and reset on success;
 * {@code last_login_at} is set on success (UTC, matching the schema's timestamp note). No
 * account is ever locked automatically — that policy decision is explicitly left for a
 * later step. {@code is_locked} stays an enforced restriction only.</p>
 */
@Service
public class AuthService {

    /**
     * The only role public registration may grant. Doctors and admins are created by
     * whatever administrative process the next steps introduce — never by this endpoint.
     */
    private static final String DEFAULT_ROLE = "PATIENT";

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /**
     * Pre-hashed throwaway for timing-attack mitigation (never a real credential; see
     * {@link #login(LoginRequest)}).
     */
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

    /**
     * Register a patient and return an already signed-in token.
     *
     * @param request validated registration data
     * @return token plus the new user's profile
     * @throws EmailAlreadyRegisteredException when that email already has an account
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalize(request.email());

        if (userRepository.findByEmail(email).isPresent()) {
            throw new EmailAlreadyRegisteredException();
        }

        Role patientRole = roleRepository.findByName(DEFAULT_ROLE)
                .orElseThrow(() -> new IllegalStateException("PATIENT role is not seeded"));

        // The plaintext password only ever touches the BCrypt encoder: from here on,
        // only the hash exists.
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

    /**
     * Sign in with email and password.
     *
     * @param request validated login data
     * @return token plus the signed-in user's profile
     * @throws InvalidCredentialsException when the email is unknown or the password is wrong
     * @throws AccountLockedException      when the account is locked (password matched)
     * @throws AccountInactiveException    when the account is deactivated (password matched)
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = normalize(request.email());
        Optional<User> maybeUser = userRepository.findByEmail(email);

        if (maybeUser.isEmpty()) {
            // Run the password check against a dummy hash anyway so the response time
            // does not reveal whether the email has an account.
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
