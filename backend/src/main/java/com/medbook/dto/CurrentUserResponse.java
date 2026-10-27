package com.medbook.dto;

import com.medbook.entity.Patient;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CurrentUserResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        String phoneNumber,
        java.util.List<String> roles,
        PatientProfileResponse patient,
        DoctorProfileResponse doctor
) {

    public record PatientProfileResponse(
            Long patientId,
            LocalDate dateOfBirth,
            String addressLine1,
            String addressLine2,
            String city,
            String stateProvince,
            String postalCode,
            String country,
            String emergencyContactName,
            String emergencyContactPhone,
            boolean hasInsurance,
            String insuranceProvider,
            String insurancePolicyNumber,
            String allergies
    ) {
        public static PatientProfileResponse from(Patient patient) {
            return new PatientProfileResponse(
                    patient.getId(),
                    patient.getDateOfBirth(),
                    patient.getAddressLine1(),
                    patient.getAddressLine2(),
                    patient.getCity(),
                    patient.getStateProvince(),
                    patient.getPostalCode(),
                    patient.getCountry(),
                    patient.getEmergencyContactName(),
                    patient.getEmergencyContactPhone(),
                    patient.hasInsurance(),
                    patient.getInsuranceProvider(),
                    patient.getInsurancePolicyNumber(),
                    patient.getAllergies()
            );
        }
    }

    public record DoctorProfileResponse(
            Long doctorId,

            String bio,
            String profilePictureUrl,

            String specialtyName,
            String medicalLicenseNumber,
            BigDecimal consultationFee,
            boolean active
    ) {
    }
}
