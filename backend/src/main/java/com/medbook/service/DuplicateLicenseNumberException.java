package com.medbook.service;

/**
 * Thrown when an admin tries to use a medical license number that another doctor
 * already has. Mapped to HTTP 409 by
 * {@link com.medbook.exception.GlobalExceptionHandler} via the business-conflict group.
 */
public class DuplicateLicenseNumberException extends RuntimeException {

    public DuplicateLicenseNumberException(String licenseNumber) {
        super("Medical license number already exists: " + licenseNumber);
    }
}
