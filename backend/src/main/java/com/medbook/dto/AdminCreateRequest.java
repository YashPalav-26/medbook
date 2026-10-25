package com.medbook.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/admin/admins} — create an administrator with their
 * login account.
 *
 * <p>One request creates the {@code users} row (BCrypt-hashed password) plus the
 * ADMIN role assignment, in one transaction. The role is fixed to ADMIN on the
 * server — this API can never mint a DOCTOR or PATIENT, and no {@code "role"}
 * field is accepted from the client.</p>
 *
 * @param email       login email (unique across users, stored lower-cased)
 * @param password    plain-text password from the admin form; only its BCrypt hash is stored
 * @param firstName   administrator's first name
 * @param lastName    administrator's last name
 * @param phoneNumber contact phone (schema allows {@code +}, digits, spaces, dashes)
 */
public record AdminCreateRequest(

        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid address")
        String email,

        @NotBlank(message = "password is required")
        @Size(min = 8, max = 72, message = "password must be 8-72 characters")
        String password,

        @NotBlank(message = "firstName is required")
        @Size(max = 100, message = "firstName must be at most 100 characters")
        String firstName,

        @NotBlank(message = "lastName is required")
        @Size(max = 100, message = "lastName must be at most 100 characters")
        String lastName,

        @NotBlank(message = "phoneNumber is required")
        @Pattern(regexp = "^\\+?[0-9\\s\\-]{1,20}$", message = "phoneNumber must be a valid phone number")
        String phoneNumber
) {
}
