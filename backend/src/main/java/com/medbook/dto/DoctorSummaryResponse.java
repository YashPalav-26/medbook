package com.medbook.dto;

import com.medbook.entity.Doctor;
import com.medbook.entity.Specialty;

import java.math.BigDecimal;

public record DoctorSummaryResponse(
        Long id,
        String firstName,
        String lastName,
        String specialty,
        int yearsExperience,
        String bio,
        BigDecimal consultationFee,
        String profilePictureUrl
) {

    public static DoctorSummaryResponse from(Doctor doctor) {
        return new DoctorSummaryResponse(
                doctor.getId(),
                doctor.getUser().getFirstName(),
                doctor.getUser().getLastName(),
                doctor.getSpecialty().getName(),
                doctor.getYearsExperience(),
                doctor.getBio(),
                doctor.getConsultationFee(),
                doctor.getProfilePictureUrl()
        );
    }

    public static DoctorSummaryResponse from(Doctor doctor, Specialty specialty) {
        return new DoctorSummaryResponse(
                doctor.getId(),
                doctor.getUser().getFirstName(),
                doctor.getUser().getLastName(),
                specialty.getName(),
                doctor.getYearsExperience(),
                doctor.getBio(),
                doctor.getConsultationFee(),
                doctor.getProfilePictureUrl()
        );
    }
}
