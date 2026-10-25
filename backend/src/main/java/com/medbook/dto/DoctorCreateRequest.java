package com.medbook.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;





















public record DoctorCreateRequest(

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
        String phoneNumber,

        @NotNull(message = "specialtyId is required")
        @Positive(message = "specialtyId must be a positive number")
        Long specialtyId,

        @NotBlank(message = "medicalLicenseNumber is required")
        @Size(max = 50, message = "medicalLicenseNumber must be at most 50 characters")
        String medicalLicenseNumber,

        @NotNull(message = "yearsExperience is required")
        @Min(value = 0, message = "yearsExperience must be between 0 and 80")
        @Max(value = 80, message = "yearsExperience must be between 0 and 80")
        Integer yearsExperience,

        @Size(max = 5000, message = "bio must be at most 5000 characters")
        String bio,

        @NotNull(message = "consultationFee is required")
        @DecimalMin(value = "0.00", message = "consultationFee must be zero or more")
        @Digits(integer = 8, fraction = 2, message = "consultationFee must have at most 8 integer and 2 decimal digits")
        BigDecimal consultationFee,

        @Size(max = 500, message = "profilePictureUrl must be at most 500 characters")
        String profilePictureUrl
) {
}
