package com.medbook.dto;

import com.medbook.entity.Doctor;

import java.math.BigDecimal;

public record DoctorAdminResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        String phoneNumber,
        Long specialtyId,
        String specialty,
        String medicalLicenseNumber,
        int yearsExperience,
        String bio,
        BigDecimal consultationFee,
        String profilePictureUrl,
        boolean active,
        boolean userActive
) {

    public static DoctorAdminResponse from(Doctor doctor) {
        return new DoctorAdminResponse(
                doctor.getId(),
                doctor.getUser().getEmail(),
                doctor.getUser().getFirstName(),
                doctor.getUser().getLastName(),
                doctor.getUser().getPhoneNumber(),
                doctor.getSpecialty().getId(),
                doctor.getSpecialty().getName(),
                doctor.getMedicalLicenseNumber(),
                doctor.getYearsExperience(),
                doctor.getBio(),
                doctor.getConsultationFee(),
                doctor.getProfilePictureUrl(),
                doctor.isActive(),
                doctor.getUser().isActive()
        );
    }
}
