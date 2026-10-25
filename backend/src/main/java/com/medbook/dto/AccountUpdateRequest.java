package com.medbook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;













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
