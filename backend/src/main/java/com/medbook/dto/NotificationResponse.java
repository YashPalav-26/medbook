package com.medbook.dto;

import com.medbook.entity.Notification;
import com.medbook.entity.NotificationType;

import java.time.LocalDateTime;
















public record NotificationResponse(
        Long id,
        NotificationType type,
        String title,
        String message,
        Long referenceId,
        boolean read,
        LocalDateTime createdAt
) {






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
