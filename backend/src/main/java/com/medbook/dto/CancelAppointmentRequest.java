package com.medbook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelAppointmentRequest(

        @NotBlank(message = "cancellationReason is required")
        @Size(max = 255, message = "cancellationReason must be at most 255 characters")
        String cancellationReason
) {
}
