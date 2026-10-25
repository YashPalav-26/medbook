package com.medbook.dto;

import com.medbook.entity.User;

import java.util.List;

/**
 * Admin-facing administrator account ({@code /api/v1/admin/admins} endpoints).
 *
 * <p>The DTO deliberately exposes only what the Admin Management screen needs:
 * identity, account state and role names. The password hash (and of course the
 * plaintext password) can never leak — a response either has a field or it
 * cannot leak.</p>
 *
 * @param id          user identifier
 * @param email       login email
 * @param firstName   first name
 * @param lastName    last name
 * @param phoneNumber contact phone
 * @param active      login account active flag ({@code users.is_active})
 * @param locked      login account locked flag ({@code users.is_locked})
 * @param roles       role names held by the account (e.g. {@code ["ADMIN"]})
 */
public record AdminAccountResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        String phoneNumber,
        boolean active,
        boolean locked,
        List<String> roles
) {

    /** Map a user to its admin-management API representation. */
    public static AdminAccountResponse from(User user) {
        return new AdminAccountResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getPhoneNumber(),
                user.isActive(),
                user.isLocked(),
                user.getRoles().stream().map(role -> role.getName()).sorted().toList()
        );
    }
}
