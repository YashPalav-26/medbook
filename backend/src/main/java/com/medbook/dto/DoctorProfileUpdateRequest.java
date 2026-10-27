package com.medbook.dto;

import jakarta.validation.constraints.Size;

public record DoctorProfileUpdateRequest(

        @Size(max = 5000, message = "bio must be at most 5000 characters")
        String bio,

        @Size(max = 500, message = "profilePictureUrl must be at most 500 characters")
        String profilePictureUrl
) {
}
