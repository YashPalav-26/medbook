package com.medbook.service;

/**
 * Thrown when a notification ID does not exist in the database — or exists but
 * belongs to a different user.
 *
 * <p>The two cases are deliberately indistinguishable: the ownership filter is
 * part of the lookup query ({@code NotificationRepository#findByIdAndUserId}), so
 * someone else's notification looks exactly like a missing row. That is the
 * project's resource-protection convention for object IDs (see
 * {@code AppointmentNotFoundException}) and it leaks nothing about which
 * notification IDs exist.</p>
 *
 * <p>{@code com.medbook.exception.GlobalExceptionHandler} maps this to HTTP 404.</p>
 */
public class NotificationNotFoundException extends RuntimeException {

    public NotificationNotFoundException(Long notificationId) {
        super("Notification not found with id: " + notificationId);
    }
}
