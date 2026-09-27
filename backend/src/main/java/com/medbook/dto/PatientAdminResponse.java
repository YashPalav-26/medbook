package com.medbook.dto;

import com.medbook.entity.Patient;

import java.time.LocalDate;

/**
 * Admin-facing patient profile ({@code /api/v1/admin/patients} endpoints).
 *
 * <p>Mirrors {@link DoctorAdminResponse} for the patient side: the account linkage
 * the admin UI needs (email, phone, active flags) on top of the patient's own
 * demographic fields. The password hash, login bookkeeping, insurance policy
 * numbers and all other security internals are never included — a response either
 * has a field or it cannot leak.</p>
 *
 * @param id          patient identifier
 * @param email       login email of the linked user
 * @param firstName   first name (from the linked user)
 * @param lastName    last name (from the linked user)
 * @param phoneNumber contact phone (from the linked user)
 * @param dateOfBirth patient's date of birth
 * @param active      patient profile active flag ({@code patients.is_active})
 * @param userActive  linked login account active flag ({@code users.is_active})
 */
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

    /** Map a patient (user initialized) to its admin API representation. */
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
