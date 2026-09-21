package com.medbook.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Request body for {@code POST /api/v1/auth/register}.
 *
 * <p>Registration always creates a <strong>patient</strong> account. There is intentionally
 * no {@code role} field: a public request can never choose ADMIN or DOCTOR. Unknown extra
 * fields (for example {@code "role": "ADMIN"}) are ignored by Jackson, never honored.</p>
 *
 * <p>Field limits mirror the schema: {@code email VARCHAR(255)}, names {@code VARCHAR(100)},
 * {@code phone_number VARCHAR(20)} with the same regular expression as the column CHECK
 * constraint, and a password of 8–72 characters (72 is BCrypt's maximum input length).</p>
 *
 * @param email        sign-in email address (unique, stored lower-cased)
 * @param password     plaintext password — only ever passed to the BCrypt encoder, never
 *                     stored, never logged
 * @param firstName    given name
 * @param lastName     family name
 * @param phoneNumber  contact number (same pattern as the schema CHECK constraint)
 * @param dateOfBirth  date of birth for the patient profile (must be in the past)
 */
public record RegisterRequest(

        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid email address")
        @Size(max = 255, message = "email must be at most 255 characters")
        String email,

        @NotBlank(message = "password is required")
        @Size(min = 8, max = 72, message = "password must be between 8 and 72 characters")
        String password,

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
        String phoneNumber,

        @NotNull(message = "dateOfBirth is required")
        @Past(message = "dateOfBirth must be in the past")
        LocalDate dateOfBirth
) {
}
