package com.medbook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;







public record SpecialtyCreateRequest(

        @NotBlank(message = "name is required")
        @Size(max = 100, message = "name must be at most 100 characters")
        String name,

        @Size(max = 5000, message = "description must be at most 5000 characters")
        String description
) {
}
