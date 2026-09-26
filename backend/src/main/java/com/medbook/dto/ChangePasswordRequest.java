package com.medbook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/v1/me/change-password}.
 *
 * <p>Limits mirror {@link RegisterRequest}: BCrypt accepts at most 72 input characters,
 * and the project's passwords are at least 8. The current password travels here so the
 * service can re-authenticate the caller before changing anything — possession of a
 * valid JWT alone is not enough, because a stolen token must not be able to lock the
 * real owner out of their account.</p>
 *
 * <p>Neither field is ever logged, echoed in an error response, or stored in plaintext;
 * the plaintext only ever reaches the BCrypt {@code PasswordEncoder}.</p>
 *
 * @param currentPassword the caller's current password (verified against the stored hash)
 * @param newPassword     the replacement password (8–72 characters)
 */
public record ChangePasswordRequest(

        @NotBlank(message = "currentPassword is required")
        String currentPassword,

        @NotBlank(message = "newPassword is required")
        @Size(min = 8, max = 72, message = "newPassword must be between 8 and 72 characters")
        String newPassword
) {
}
