package com.medbook.dto;

import java.util.List;


















public record AuthResponse(
        String token,
        String tokenType,
        long expiresInMinutes,
        UserSummary user
) {










    public record UserSummary(
            Long id,
            String email,
            String firstName,
            String lastName,
            List<String> roles
    ) {
    }
}
