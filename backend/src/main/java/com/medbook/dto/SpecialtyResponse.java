package com.medbook.dto;

import com.medbook.entity.Specialty;

public record SpecialtyResponse(
        Long id,
        String name,
        String description,
        boolean active
) {

    public static SpecialtyResponse from(Specialty specialty) {
        return new SpecialtyResponse(specialty.getId(), specialty.getName(), specialty.getDescription(),
                specialty.isActive());
    }
}
