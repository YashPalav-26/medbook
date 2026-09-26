package com.medbook.dto;

import com.medbook.entity.Patient;
import com.medbook.entity.User;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The authenticated user's own profile, from {@code GET /api/v1/me/profile}.
 *
 * <p>One response for every role: the account block is always present, the role-specific
 * blocks are filled by whichever profile row exists (a patient sees {@code patient},
 * a doctor sees {@code doctor}, an admin sees only the account block). Fields the
 * caller can update are marked in the field docs; everything else is system- or
 * admin-controlled.</p>
 *
 * <p><strong>Never serialized:</strong> {@code password_hash}, {@code failed_login_attempts},
 * {@code is_locked}, {@code last_login_at} and the generated audit columns — the safe
 * account fields come straight from {@link AuthResponse.UserSummary} conventions.</p>
 *
 * @param id        user ID (matches the JWT subject)
 * @param email     sign-in email (read-only: the account's sign-in identity)
 * @param firstName given name (editable)
 * @param lastName  family name (editable)
 * @param phoneNumber contact number (editable, schema CHECK pattern)
 * @param roles     Spring Security authorities, e.g. {@code "ROLE_PATIENT"}
 * @param patient   the caller's patient profile, {@code null} unless the caller has one
 * @param doctor    the caller's doctor profile, {@code null} unless the caller has one
 */
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

    /** The editable patient medical/contact profile ({@code patients} table fields). */
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

    /** The doctor's professional profile, split into doctor-editable and admin-controlled. */
    public record DoctorProfileResponse(
            Long doctorId,
            // --- doctor-editable (own profile) ---
            String bio,
            String profilePictureUrl,
            // --- admin-controlled (read-only for the doctor) ---
            String specialtyName,
            String medicalLicenseNumber,
            BigDecimal consultationFee,
            boolean active
    ) {
    }
}
