package com.medbook.controller;

import com.medbook.dto.AuthResponse;
import com.medbook.dto.LoginRequest;
import com.medbook.dto.RegisterRequest;
import com.medbook.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public authentication endpoints.
 *
 * <p>Both of these stay outside the Bearer-token fence on purpose: they are how a client
 * obtains a token in the first place.</p>
 *
 * <pre>
 * POST /api/v1/auth/register  -&gt; 201 Created : patient self-registration (PATIENT role only)
 * POST /api/v1/auth/login     -&gt; 200 OK      : email/password sign-in
 * </pre>
 *
 * <p>The controller is thin like {@link AppointmentController}: validate the DTO, call the
 * service, return the token response. Password hashing, duplicate checks, role assignment,
 * failure counting, and token creation all live in {@link AuthService} and
 * {@link com.medbook.security.JwtService}.</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Register a patient and return an already signed-in token.
     *
     * <p>The request cannot pick a role: every account created here is a PATIENT.</p>
     *
     * @param request validated registration data
     * @return {@code 201 Created} with token and profile, {@code 409} when the email is taken
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    /**
     * Sign in with email and password.
     *
     * @param request validated login data
     * @return {@code 200 OK} with token and profile, {@code 401} for any wrong answer
     */
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
