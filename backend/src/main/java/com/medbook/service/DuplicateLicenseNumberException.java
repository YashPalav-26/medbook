package com.medbook.service;

public class DuplicateLicenseNumberException extends RuntimeException {

    public DuplicateLicenseNumberException(String licenseNumber) {
        super("Medical license number already exists: " + licenseNumber);
    }
}
