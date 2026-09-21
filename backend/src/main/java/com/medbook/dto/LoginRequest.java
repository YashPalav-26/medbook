package com.medbook.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/v1/auth/login}.
 *
 * <p>Only blankness is validated here. No email-format or password-strength check is
 * applied on purpose: the whole point of login is to answer "do these credentials match an
 * account?" with either a token or the same generic 401 for every wrong answer. Stricter
 * format validation would only change which requests produce 400 vs 401 without adding
 * security.</p>
 *
 * @param email    the sign-in email address
 * @param password the plaintext password, compared against the stored BCrypt hash
 */
public record LoginRequest(

        @NotBlank(message = "email is required")
        String email,

        @NotBlank(message = "password is required")
        String password
) {
}
