package com.medbook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PUT /api/v1/admin/specialties/{id}} — edit a specialty.
 * Deactivation is a separate PATCH so an accidental rename cannot retire a specialty.
 *
 * @param name        new unique display name
 * @param description new description (nullable)
 */
public record SpecialtyUpdateRequest(

        @NotBlank(message = "name is required")
        @Size(max = 100, message = "name must be at most 100 characters")
        String name,

        @Size(max = 5000, message = "description must be at most 5000 characters")
        String description
) {
}
