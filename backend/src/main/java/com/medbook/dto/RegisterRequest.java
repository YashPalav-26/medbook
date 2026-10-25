package com.medbook.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;




















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
