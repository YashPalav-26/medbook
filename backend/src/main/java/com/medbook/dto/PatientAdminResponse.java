package com.medbook.dto;

import com.medbook.entity.Patient;

import java.time.LocalDate;

public record PatientAdminResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        String phoneNumber,
        LocalDate dateOfBirth,
        boolean active,
        boolean userActive
) {

    public static PatientAdminResponse from(Patient patient) {
        return new PatientAdminResponse(
                patient.getId(),
                patient.getUser().getEmail(),
                patient.getUser().getFirstName(),
                patient.getUser().getLastName(),
                patient.getUser().getPhoneNumber(),
                patient.getDateOfBirth(),
                patient.isActive(),
                patient.getUser().isActive()
        );
    }
}
