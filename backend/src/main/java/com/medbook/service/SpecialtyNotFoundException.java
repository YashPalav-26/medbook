package com.medbook.service;

public class SpecialtyNotFoundException extends RuntimeException {

    public SpecialtyNotFoundException(Long specialtyId) {
        super("Specialty not found with id: " + specialtyId);
    }
}
