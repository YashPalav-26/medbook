package com.medbook.entity;

/**
 * The kinds of in-app notification MedBook sends.
 *
 * <p>Each value corresponds to one appointment lifecycle event, matching the
 * {@code CHECK (type IN (...))} constraint in {@code V2__create_notifications.sql}.
 * Stored as VARCHAR(50) with {@code @Enumerated(EnumType.STRING)} — like
 * {@link AppointmentStatus}, the enum name is the database value.</p>
 */
public enum NotificationType {

    /** The user's (or, for the doctor, a new) appointment was booked. */
    APPOINTMENT_CREATED,

    /** The assigned doctor confirmed the appointment. */
    APPOINTMENT_CONFIRMED,

    /** The appointment was cancelled by the patient, the doctor, or clinic support. */
    APPOINTMENT_CANCELLED,

    /** The visit was completed by the doctor. */
    APPOINTMENT_COMPLETED,

    /** The doctor marked the appointment as a no-show. */
    APPOINTMENT_NO_SHOW
}
