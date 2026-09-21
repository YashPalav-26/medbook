package com.medbook.dto;

import com.medbook.entity.Specialty;

/**
 * Active specialty for the booking UI ({@code GET /api/v1/specialties}).
 *
 * <p>A small, explicit contract: the identifier used when filtering doctors, the display
 * name, and the optional description. Internal bookkeeping ({@code created_at}) and
 * inactive rows never appear here.</p>
 *
 * @param id          specialty identifier
 * @param name        display name, for example {@code "Cardiology"}
 * @param description short description (may be {@code null})
 */
public record SpecialtyResponse(
        Long id,
        String name,
        String description
) {

    /** Map a specialty to its API representation. */
    public static SpecialtyResponse from(Specialty specialty) {
        return new SpecialtyResponse(specialty.getId(), specialty.getName(), specialty.getDescription());
    }
}
