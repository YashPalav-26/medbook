package com.medbook.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PatientProfileUpdateRequest(

        @NotNull(message = "addressLine1 is required (send an empty string to clear it)")
        @Size(max = 255, message = "addressLine1 must be at most 255 characters")
        String addressLine1,

        @NotNull(message = "addressLine2 is required (send an empty string to clear it)")
        @Size(max = 255, message = "addressLine2 must be at most 255 characters")
        String addressLine2,

        @NotNull(message = "city is required (send an empty string to clear it)")
        @Size(max = 100, message = "city must be at most 100 characters")
        String city,

        @NotNull(message = "stateProvince is required (send an empty string to clear it)")
        @Size(max = 50, message = "stateProvince must be at most 50 characters")
        String stateProvince,

        @NotNull(message = "postalCode is required (send an empty string to clear it)")
        @Size(max = 20, message = "postalCode must be at most 20 characters")
        String postalCode,

        @NotNull(message = "country is required (send an empty string to clear it)")
        @Size(max = 50, message = "country must be at most 50 characters")
        String country,

        @NotNull(message = "emergencyContactName is required (send an empty string to clear it)")
        @Size(max = 200, message = "emergencyContactName must be at most 200 characters")
        String emergencyContactName,

        @NotNull(message = "emergencyContactPhone is required (send an empty string to clear it)")
        @Size(max = 20, message = "emergencyContactPhone must be at most 20 characters")
        @Pattern(regexp = "^\\+?[0-9\\s\\-]*$", message = "emergencyContactPhone may only contain digits, spaces, dashes and an optional leading +")
        String emergencyContactPhone,

        boolean hasInsurance,

        @NotNull(message = "insuranceProvider is required (send an empty string to clear it)")
        @Size(max = 100, message = "insuranceProvider must be at most 100 characters")
        String insuranceProvider,

        @NotNull(message = "insurancePolicyNumber is required (send an empty string to clear it)")
        @Size(max = 50, message = "insurancePolicyNumber must be at most 50 characters")
        String insurancePolicyNumber,

        @NotNull(message = "allergies is required (send an empty string to clear it)")
        @Size(max = 5000, message = "allergies must be at most 5000 characters")
        String allergies
) {
}
