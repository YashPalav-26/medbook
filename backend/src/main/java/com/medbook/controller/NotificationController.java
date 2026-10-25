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















@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }


    @GetMapping
    public ResponseEntity<List<NotificationResponse>> getCurrentUserNotifications() {
        return ResponseEntity.ok(notificationService.getCurrentUserNotifications());
    }


    @GetMapping("/unread-count")
    public ResponseEntity<Long> getUnreadCount() {
        return ResponseEntity.ok(notificationService.getCurrentUserUnreadCount());
    }


    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markAsRead(@PathVariable Long id) {
        return ResponseEntity.ok(notificationService.markAsRead(id));
    }


    @PostMapping("/read-all")
    public ResponseEntity<Long> markAllAsRead() {
        return ResponseEntity.ok(notificationService.markAllAsRead());
    }
}
