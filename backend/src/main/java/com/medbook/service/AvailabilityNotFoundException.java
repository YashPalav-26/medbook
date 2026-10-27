package com.medbook.service;

public class AvailabilityNotFoundException extends RuntimeException {

    public AvailabilityNotFoundException(Long availabilityId) {
        super("Availability window not found with id: " + availabilityId);
    }
}
