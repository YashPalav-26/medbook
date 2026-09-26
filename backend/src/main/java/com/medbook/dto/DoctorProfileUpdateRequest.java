package com.medbook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code PUT /api/v1/me/doctor-profile} — the DOCTOR-editable slice of
 * the professional profile.
 *
 * <p>The current application design keeps the professional registry admin-controlled:
 * {@code specialtyId}, {@code medicalLicenseNumber}, {@code consultationFee} and the
 * active flag are changed by clinic administration through {@code /api/v1/admin/doctors}
 * (see {@code DoctorUpdateRequest}). A doctor therefore edits exactly what describes
 * their own practice in their own words — biography and avatar — and nothing that
 * administration owns. The type simply has no other fields, so a client cannot send
 * them and Jackson cannot honor them.</p>
 *
 * @param bio               optional professional biography (max 5000, mirrors the TEXT column)
 * @param profilePictureUrl optional avatar URL (max 500)
 */
public record DoctorProfileUpdateRequest(

        @Size(max = 5000, message = "bio must be at most 5000 characters")
        String bio,

        @Size(max = 500, message = "profilePictureUrl must be at most 500 characters")
        String profilePictureUrl
) {
}
