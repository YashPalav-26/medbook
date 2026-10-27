package com.medbook.dto;

import com.medbook.entity.User;

import java.util.List;

public record AdminAccountResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        String phoneNumber,
        boolean active,
        boolean locked,
        List<String> roles
) {

    public static AdminAccountResponse from(User user) {
        return new AdminAccountResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getPhoneNumber(),
                user.isActive(),
                user.isLocked(),
                user.getRoles().stream().map(role -> role.getName()).sorted().toList()
        );
    }
}
