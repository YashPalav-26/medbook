package com.medbook.controller;

import com.medbook.dto.AccountUpdateRequest;
import com.medbook.dto.ChangePasswordRequest;
import com.medbook.dto.CurrentUserResponse;
import com.medbook.dto.DoctorProfileUpdateRequest;
import com.medbook.dto.PatientProfileUpdateRequest;
import com.medbook.service.ProfileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The authenticated caller's own account and profile ("me" API).
 *
 * <p>Every endpoint resolves the caller from the JWT — a user ID is never accepted from
 * the request — so there is no route that can address another user's data. Routing is
 * flat: one profile endpoint whose response carries the role-specific block(s), and one
 * update endpoint per editable surface, dispatched by the service on what the caller
 * actually is.</p>
 *
 * <p>Authorization: {@code /api/v1/me/**} falls under {@code anyRequest().authenticated()}
 * in the security configuration — any valid JWT may call it, and the service restricts
 * each update to the profile matching the caller's own row. Anonymous callers get 401
 * from the authentication entry point.</p>
 */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final ProfileService profileService;

    public MeController(ProfileService profileService) {
        this.profileService = profileService;
    }

    /** The caller's own profile: account block plus the role-specific profile if one exists. */
    @GetMapping("/profile")
    public ResponseEntity<CurrentUserResponse> getProfile() {
        return ResponseEntity.ok(profileService.getCurrentUserProfile());
    }

    /**
     * Update the caller's own account fields (name + phone). All roles.
     * 400 on validation failure.
     */
    @PutMapping("/account")
    public ResponseEntity<CurrentUserResponse> updateAccount(@Valid @RequestBody AccountUpdateRequest request) {
        return ResponseEntity.ok(profileService.updateAccount(request));
    }

    /**
     * Update the caller's own patient profile. 403 when the caller has no patient row
     * (doctors/admins), 400 on validation failure.
     */
    @PutMapping("/patient-profile")
    public ResponseEntity<CurrentUserResponse> updatePatientProfile(
            @Valid @RequestBody PatientProfileUpdateRequest request) {
        return ResponseEntity.ok(profileService.updatePatientProfile(request));
    }

    /**
     * Update the caller's own doctor profile (bio + picture only). 403 when the caller
     * has no doctor row, 400 on validation failure.
     */
    @PutMapping("/doctor-profile")
    public ResponseEntity<CurrentUserResponse> updateDoctorProfile(
            @Valid @RequestBody DoctorProfileUpdateRequest request) {
        return ResponseEntity.ok(profileService.updateDoctorProfile(request));
    }

    /**
     * Change the caller's password (current password required, new one 8–72 chars).
     * 401 wrong current password. Existing JWTs stay valid until expiry (stateless
     * token model — no revocation is implemented).
     */
    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        profileService.changePassword(request);
        return ResponseEntity.noContent().build();
    }
}
