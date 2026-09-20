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
 * JPA entity mapping the {@code appointment_status_history} table.
 *
 * <p>Represents a historical record of a status change for an appointment.
 * Acts as an audit trail, recording when status changed, who initiated it, and why.
 * These records are immutable once created (no updates).</p>
 *
 * <p>Relationships:
 * <ul>
 * <li>AppointmentStatusHistory → Appointment: Many-to-one. Many status changes belong to one appointment.</li>
 * <li>AppointmentStatusHistory → User: Many-to-one (optional). The user who changed the status.
 *     ON DELETE SET NULL at database level allows tracking of historical changes even if the user is deleted.</li>
 * </ul>
 * </p>
 */
@Entity
@Table(name = "appointment_status_history")
public class AppointmentStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * Many-to-one relationship to Appointment.
     * The foreign key {@code appointment_id} references {@code appointments(id)}.
     * ON DELETE RESTRICT at database level (appointment history cannot be deleted if appointment exists).
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appointment_id", nullable = false)
    private Appointment appointment;

    /**
     * The status of the appointment at the time of this history record.
     * Stored as VARCHAR(20), mapped to Java enum.
     */
    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private AppointmentStatus status;

    /**
     * Many-to-one relationship to User (the person who made the status change).
     * The foreign key {@code changed_by_user_id} references {@code users(id)}.
     * ON DELETE SET NULL at database level: if the user is deleted, this field becomes NULL,
     * but the audit record is preserved.
     *
     * <p>Optional because the user who changed the status may later be deleted from the system.</p>
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "changed_by_user_id")
    private User changedByUser;

    /**
     * Notes or reason for the status change.
     * Optional.
     */
    @Column(name = "notes", length = 500)
    private String notes;

    /**
     * Timestamp when this status change occurred.
     * Database-generated default: CURRENT_TIMESTAMP(3).
     * Marked insertable=false so MySQL supplies the default.
     */
    @Column(name = "changed_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime changedAt;

    /**
     * No-arg constructor required by JPA/Hibernate.
     */
    public AppointmentStatusHistory() {
    }

    public AppointmentStatusHistory(Appointment appointment, AppointmentStatus status) {
        this.appointment = appointment;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public Appointment getAppointment() {
        return appointment;
    }

    public void setAppointment(Appointment appointment) {
        this.appointment = appointment;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public void setStatus(AppointmentStatus status) {
        this.status = status;
    }

    public User getChangedByUser() {
        return changedByUser;
    }

    public void setChangedByUser(User changedByUser) {
        this.changedByUser = changedByUser;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AppointmentStatusHistory that)) {
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

    @Override
    public String toString() {
        return "AppointmentStatusHistory{id=" + id
                + ", status=" + status
                + ", notes='" + notes + "'"
                + ", changedAt=" + changedAt + "}";
    }
}
