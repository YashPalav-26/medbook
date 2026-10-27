package com.medbook.service;

public class DuplicateAvailabilitySlotException extends RuntimeException {

    public DuplicateAvailabilitySlotException() {
        super("This availability window already exists for the doctor");
    }
}
