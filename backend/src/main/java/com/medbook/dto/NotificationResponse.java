package com.medbook.dto;

import com.medbook.entity.Notification;
import com.medbook.entity.NotificationType;

import java.time.LocalDateTime;

/**
 * One notification as exposed through {@code /api/v1/notifications} (Step 25).
 *
 * <p>Response-only DTO: never serialize the JPA entity — the entity holds a lazy
 * {@code User} proxy that would be walked (or blow up) during JSON mapping, and
 * the recipient's account fields must never leave the server.</p>
 *
 * @param id          notification identifier
 * @param type        which lifecycle event produced it
 * @param title       short human-readable headline
 * @param message     longer human-readable detail
 * @param referenceId the related appointment's ID, when the notification is about one
 * @param read        whether the recipient has seen it
 * @param createdAt   when the notification was created (database-generated)
 */
public record NotificationResponse(
        Long id,
        NotificationType type,
        String title,
        String message,
        Long referenceId,
        boolean read,
        LocalDateTime createdAt
) {

    /**
     * Map a persisted notification to its API representation. Call inside the
     * service transaction — the recipient {@code user} is never touched, so no
     * lazy association is actually resolved here.
     */
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getReferenceId(),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }
}
