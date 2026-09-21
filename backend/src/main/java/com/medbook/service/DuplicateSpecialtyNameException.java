package com.medbook.service;

/**
 * Thrown when an admin tries to create or rename a specialty to a name that
 * already exists (case-insensitive). Mapped to HTTP 409 by
 * {@link com.medbook.exception.GlobalExceptionHandler} via the business-conflict group.
 */
public class DuplicateSpecialtyNameException extends RuntimeException {

    public DuplicateSpecialtyNameException(String name) {
        super("Specialty name already exists: " + name);
    }
}
