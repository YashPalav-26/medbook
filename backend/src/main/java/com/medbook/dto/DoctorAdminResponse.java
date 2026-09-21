package com.medbook.dto;

import com.medbook.entity.Doctor;

import java.math.BigDecimal;

/**
 * Admin-facing doctor profile ({@code /api/v1/admin/doctors} endpoints).
 *
 * <p>Adds the account linkage the admin UI needs (which email logs this doctor in,
 * whether the account and profile are active) on top of the booking-facing
 * {@link DoctorSummaryResponse} fields. The password hash, login bookkeeping and
 * all other security internals are never included — a response either has a field
 * or it cannot leak.</p>
 *
 * @param id                doctor identifier
 * @param email             login email of the linked user
 * @param firstName         first name (from the linked user)
 * @param lastName          last name (from the linked user)
 * @param phoneNumber       contact phone (from the linked user)
 * @param specialtyId       specialty FK
 * @param specialty         specialty display name
 * @param medicalLicenseNumber unique license number
 * @param yearsExperience   years of practice
 * @param bio               biography (nullable)
 * @param consultationFee   current fee
 * @param profilePictureUrl avatar URL (nullable)
 * @param active            doctor profile active flag ({@code doctors.is_active})
 * @param userActive        linked login account active flag ({@code users.is_active})
 */
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

    /** Map a doctor (user + specialty initialized) to its admin API representation. */
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
