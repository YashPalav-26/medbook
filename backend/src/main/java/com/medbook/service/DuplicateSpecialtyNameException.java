package com.medbook.service;

public class DuplicateSpecialtyNameException extends RuntimeException {

    public DuplicateSpecialtyNameException(String name) {
        super("Specialty name already exists: " + name);
    }
}
