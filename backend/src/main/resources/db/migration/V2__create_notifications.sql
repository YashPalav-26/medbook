-- V2: Create notifications table (Step 25 — in-app notification system)
-- Additive only: V1 is never modified. A notification is a persistent, user-specific
-- message about an appointment lifecycle event (created/confirmed/cancelled/completed/
-- no-show), shown in the signed-in user's notification bell.

-- ============================================================================
-- Table: notifications
-- Purpose: In-app notifications for the authenticated user
-- ============================================================================
CREATE TABLE notifications (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    type VARCHAR(50) NOT NULL,
    title VARCHAR(100) NOT NULL,
    message VARCHAR(500) NOT NULL,
    reference_id BIGINT UNSIGNED,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,

    INDEX idx_notifications_user_created (user_id, created_at DESC),
    INDEX idx_notifications_user_unread (user_id, is_read),

    CHECK (type IN ('APPOINTMENT_CREATED','APPOINTMENT_CONFIRMED','APPOINTMENT_CANCELLED','APPOINTMENT_COMPLETED','APPOINTMENT_NO_SHOW'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
