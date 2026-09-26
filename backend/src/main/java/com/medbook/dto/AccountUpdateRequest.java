package com.medbook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code PUT /api/v1/me/account} — the account fields every role may
 * edit for themselves: given name, family name, contact phone.
 *
 * <p>Email is deliberately absent: it is the sign-in identity and the uniqueness anchor
 * every lookup keys on, so self-service email changes are out of scope. Limits and the
 * phone pattern mirror {@link RegisterRequest} and the {@code users} schema columns.</p>
 *
 * @param firstName   given name (max 100)
 * @param lastName    family name (max 100)
 * @param phoneNumber contact number (digits/spaces/dashes, max 20)
 */
public record AccountUpdateRequest(

        @NotBlank(message = "firstName is required")
        @Size(max = 100, message = "firstName must be at most 100 characters")
        String firstName,

        @NotBlank(message = "lastName is required")
        @Size(max = 100, message = "lastName must be at most 100 characters")
        String lastName,

        @NotBlank(message = "phoneNumber is required")
        @Size(max = 20, message = "phoneNumber must be at most 20 characters")
        @Pattern(regexp = "^\\+?[0-9\\s\\-]+$",
                message = "phoneNumber may only contain digits, spaces, dashes and an optional leading +")
        String phoneNumber
) {
}
