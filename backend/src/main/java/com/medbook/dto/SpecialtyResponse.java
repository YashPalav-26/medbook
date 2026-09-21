package com.medbook.dto;

import com.medbook.entity.Specialty;

/**
 * Specialty for the booking UI ({@code GET /api/v1/specialties}) and the admin list
 * ({@code GET /api/v1/admin/specialties}).
 *
 * <p>A small, explicit contract: the identifier used when filtering doctors, the display
 * name, the optional description, and the active flag. Internal bookkeeping
 * ({@code created_at}) never appears. The public discovery endpoint only ever returns
 * active rows; the admin list uses the same DTO and surfaces both states.</p>
 *
 * @param id          specialty identifier
 * @param name        display name, for example {@code "Cardiology"}
 * @param description short description (may be {@code null})
 * @param active      whether the specialty is currently offered for booking
 */
public record SpecialtyResponse(
        Long id,
        String name,
        String description,
        boolean active
) {

    /** Map a specialty to its API representation. */
    public static SpecialtyResponse from(Specialty specialty) {
        return new SpecialtyResponse(specialty.getId(), specialty.getName(), specialty.getDescription(),
                specialty.isActive());
    }
}
