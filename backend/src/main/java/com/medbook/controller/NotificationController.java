package com.medbook.controller;

import com.medbook.dto.NotificationResponse;
import com.medbook.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * In-app notification REST API (Step 25) — the authenticated caller's own notifications.
 *
 * <p>Every endpoint resolves the caller from the JWT; no {@code userId} is ever
 * accepted from a request, so there is no route that can address another user's
 * notifications. Accessing a notification ID that does not exist <em>or</em> belongs
 * to someone else returns 404 (the two are indistinguishable by design — see
 * {@code NotificationNotFoundException}).</p>
 *
 * <p>Authorization: {@code /api/v1/notifications/**} falls under
 * {@code anyRequest().authenticated()} in the security configuration, so anonymous
 * callers get 401 from the authentication entry point and no SecurityConfig change
 * was needed.</p>
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** The caller's notifications, newest first. */
    @GetMapping
    public ResponseEntity<List<NotificationResponse>> getCurrentUserNotifications() {
        return ResponseEntity.ok(notificationService.getCurrentUserNotifications());
    }

    /** The caller's unread count — the number shown on the bell badge. */
    @GetMapping("/unread-count")
    public ResponseEntity<Long> getUnreadCount() {
        return ResponseEntity.ok(notificationService.getCurrentUserUnreadCount());
    }

    /** Mark one of the caller's own notifications as read. 404 for unknown/not-owned IDs. */
    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markAsRead(@PathVariable Long id) {
        return ResponseEntity.ok(notificationService.markAsRead(id));
    }

    /** Mark all of the caller's unread notifications as read; returns the new unread count. */
    @PostMapping("/read-all")
    public ResponseEntity<Long> markAllAsRead() {
        return ResponseEntity.ok(notificationService.markAllAsRead());
    }
}
