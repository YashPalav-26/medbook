package com.medbook.dto;

import java.util.List;

/**
 * Response body for {@code POST /api/v1/auth/register} and
 * {@code POST /api/v1/auth/login}.
 *
 * <p>The client passes {@code token} back as {@code Authorization: Bearer <token>} on every
 * protected request. {@code tokenType} names the scheme explicitly, and
 * {@code expiresInMinutes} tells the client when to ask for a new token.</p>
 *
 * <p>The embedded {@link UserSummary} is the user's own public profile — it never contains
 * the password hash or internal account flags ({@code is_locked},
 * {@code failed_login_attempts}).</p>
 *
 * @param token            signed JWT
 * @param tokenType        always {@code "Bearer"}
 * @param expiresInMinutes token lifetime configured on the server
 * @param user             the signed-in user's own profile
 */
public record AuthResponse(
        String token,
        String tokenType,
        long expiresInMinutes,
        UserSummary user
) {

    /**
     * The signed-in user's public profile.
     *
     * @param id        user ID, matching the JWT {@code sub} claim
     * @param email     sign-in email
     * @param firstName given name
     * @param lastName  family name
     * @param roles     Spring Security authorities, for example {@code "ROLE_PATIENT"}
     */
    public record UserSummary(
            Long id,
            String email,
            String firstName,
            String lastName,
            List<String> roles
    ) {
    }
}
