package com.medbook.entity;

/**
 * Enumeration representing possible appointment statuses.
 *
 * <p>Stored as VARCHAR in the database with {@link javax.persistence.EnumType#STRING}.
 * Values must exactly match the CHECK constraint in the Flyway schema:</p>
 *
 * <pre>
 * CHECK (status IN ('SCHEDULED','CONFIRMED','COMPLETED','CANCELLED','NO_SHOW'))
 * </pre>
 */
public enum AppointmentStatus {

    SCHEDULED,
    CONFIRMED,
    COMPLETED,
    CANCELLED,
    NO_SHOW
}