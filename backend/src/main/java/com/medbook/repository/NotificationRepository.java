package com.medbook.repository;

import com.medbook.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Notification} entities (Step 25).
 *
 * <p>Every method is user-scoped: there is deliberately no {@code findAll()},
 * {@code findById}-without-owner, or delete method — a notification can only ever
 * be read or flipped to read in the context of its owner, which is how the
 * service enforces "users may access ONLY their own notifications".</p>
 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * The recipient's notifications, newest first.
     * Generated query: {@code SELECT * FROM notifications WHERE user_id = ? ORDER BY created_at DESC}.
     */
    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * How many unread notifications the recipient has (the bell badge).
     * Generated query: {@code SELECT COUNT(*) FROM notifications WHERE user_id = ? AND is_read = FALSE}.
     * (The JavaBeans property is {@code read} — from the {@code isRead()} getter.)
     */
    long countByUserIdAndReadFalse(Long userId);

    /**
     * One notification by ID <em>and</em> owner. The ownership filter is part of the
     * query itself, so a notification belonging to someone else looks exactly like a
     * missing row — the conventional 404 for "not yours" (same treatment as
     * appointment IDs in the tests of this project).
     */
    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    /**
     * Mark every unread notification of one user as read in a single statement.
     *
     * <p>A bulk JPQL {@code UPDATE} bypasses the persistence context, so callers must
     * annotate the surrounding service method with
     * {@code @Modifying(clearAutomatically = true)} semantics — the service layer's
     * transaction handles this (see {@code NotificationService#markAllAsRead}).</p>
     */
    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.user.id = :userId AND n.read = false")
    int markAllReadForUser(@Param("userId") Long userId);
}
