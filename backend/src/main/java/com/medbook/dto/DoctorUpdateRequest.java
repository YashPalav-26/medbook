package com.medbook.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Body of {@code PUT /api/v1/admin/doctors/{id}} — edit a doctor's profile.
 *
 * <p>Login credentials (email, password) and the linked user's name/phone are
 * deliberately out of scope for this endpoint: they belong to account management,
 * not profile management. Deactivation is a separate PATCH.</p>
 *
 * @param specialtyId       new specialty (must exist)
 * @param medicalLicenseNumber new license number (unique)
 * @param yearsExperience   years of practice (0–80)
 * @param bio               optional biography
 * @param consultationFee   fee (>= 0)
 * @param profilePictureUrl optional avatar URL
 */
public record DoctorUpdateRequest(

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
