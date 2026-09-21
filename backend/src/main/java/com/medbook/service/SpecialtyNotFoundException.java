package com.medbook.service;

/**
 * Thrown when an admin operation references a specialty that does not exist.
 * Extends {@link PatientNotFoundException}'s sibling family — mapped to 404 by
 * {@link com.medbook.exception.GlobalExceptionHandler} via the not-found group.
 */
public class SpecialtyNotFoundException extends RuntimeException {

    public SpecialtyNotFoundException(Long specialtyId) {
        super("Specialty not found with id: " + specialtyId);
    }
}
