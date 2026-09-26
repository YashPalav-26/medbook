package com.medbook.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * JPA entity mapping the {@code notifications} table (Step 25).
 *
 * <p>A notification is a short, persistent, user-specific message about an
 * appointment lifecycle event. It belongs to exactly one {@link User} — the
 * recipient — and never duplicates patient/doctor identity fields: whoever
 * should see it simply owns the row.</p>
 *
 * <p>Notifications are write-once-then-mark-read: {@code title}, {@code message},
 * {@code type} and {@code referenceId} never change after creation; only
 * {@code isRead} flips. {@code createdAt} is database-generated like every other
 * table in this schema.</p>
 *
 * <p>Relationships:
 * <ul>
 * <li>Notification → User: Many-to-one. Many notifications belong to one user.</li>
 * </ul>
 */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * Many-to-one relationship to the recipient.
     * The foreign key {@code user_id} references {@code users(id)}.
     * ON DELETE CASCADE at database level: notifications are meaningless without
     * their owner, so they disappear with the user.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Which lifecycle event produced this notification.
     * Stored as VARCHAR(50), mapped to Java enum — same strategy as
     * {@link AppointmentStatus}.
     */
    @Column(name = "type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private NotificationType type;

    /** Short human-readable headline, for example "Appointment confirmed". */
    @Column(name = "title", nullable = false, length = 100)
    private String title;

    /** Longer human-readable detail, for example "Dr. Wells confirmed your visit on 2026-10-01 at 10:00". */
    @Column(name = "message", nullable = false, length = 500)
    private String message;

    /**
     * The related appointment's ID, when the notification is about one.
     * Nullable on purpose: the schema keeps the door open for notifications
     * that are not about an appointment. Today every type is appointment-bound,
     * but the column costs nothing and avoids a schema change later.
     */
    @Column(name = "reference_id")
    private Long referenceId;

    /** Whether the recipient has seen this notification. Defaults to false (unread). */
    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    /**
     * Database-generated timestamp. Marked insertable=false so MySQL supplies
     * the DEFAULT CURRENT_TIMESTAMP(3) on INSERT — same convention as the
     * rest of the schema.
     *
     * <p>Note: because the value is written by MySQL, the field is null on the
     * in-memory instance until the row is re-read (new persistence context or
     * after a flush + clear). Every HTTP read runs in its own transaction, so
     * API responses always carry a real timestamp; only same-transaction test
     * assertions need an explicit refresh first.</p>
     */
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    /** No-arg constructor required by JPA/Hibernate. */
    public Notification() {
    }

    public Notification(User user, NotificationType type, String title, String message, Long referenceId) {
        this.user = user;
        this.type = type;
        this.title = title;
        this.message = message;
        this.referenceId = referenceId;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public NotificationType getType() {
        return type;
    }

    public void setType(NotificationType type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Long getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(Long referenceId) {
        this.referenceId = referenceId;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Notification that)) {
            return false;
        }
        if (id == null || that.id == null) {
            return false;
        }
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id == null ? UUID.randomUUID().hashCode() : id.hashCode();
    }

    /** Omits {@code user} to avoid lazy-proxy access in logs. */
    @Override
    public String toString() {
        return "Notification{id=" + id
                + ", type=" + type
                + ", title='" + title + "'"
                + ", referenceId=" + referenceId
                + ", read=" + read
                + ", createdAt=" + createdAt + "}";
    }
}
