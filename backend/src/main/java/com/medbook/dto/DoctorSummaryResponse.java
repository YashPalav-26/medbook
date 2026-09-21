package com.medbook.dto;

import com.medbook.entity.Doctor;
import com.medbook.entity.Specialty;

import java.math.BigDecimal;

/**
 * Booking-facing doctor profile for discovery APIs ({@code GET /api/v1/doctors}).
 *
 * <p><strong>Why a DTO and not the JPA entity?</strong> Same reasoning as
 * {@link AppointmentResponse}: the JSON contract must be explicit and stable, and the
 * entity graph contains data a booking page must never see — the linked {@code users}
 * row carries the password hash, lock flags and login bookkeeping. This record exposes
 * only what a patient needs to pick a doctor.</p>
 *
 * <p>Mapping needs the doctor's {@code user} and {@code specialty} associations, so the
 * repository methods that feed it use fetch joins (Open Session In View is disabled).</p>
 *
 * @param id                doctor identifier (the value used for booking)
 * @param firstName         doctor's first name (from the linked user)
 * @param lastName          doctor's last name (from the linked user)
 * @param specialty         specialty name, for example {@code "Cardiology"}
 * @param yearsExperience   years of practice
 * @param bio               short professional biography (may be {@code null})
 * @param consultationFee   current consultation fee
 * @param profilePictureUrl avatar URL (may be {@code null})
 */
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

    /** Map a doctor (with user + specialty initialized) to its API representation. */
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

    /** Convenience overload when the specialty is already at hand. */
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
