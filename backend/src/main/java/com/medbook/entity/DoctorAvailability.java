package com.medbook.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * JPA entity mapping the {@code doctor_availability} table.
 *
 * <p>Represents a doctor's weekly availability time slots. Each record defines
 * a time window when a doctor is available for appointments on a specific day
 * of the week.</p>
 *
 * <p>This is a <strong>configuration table</strong>, not an appointment record.
 * Appointments are stored in the separate {@code appointments} table. This
 * entity merely defines <em>when</em> a doctor can accept appointments.</p>
 *
 * <h3>Relationship: DoctorAvailability → Doctor (Many-to-One)</h3>
 * <p>Many availability records can belong to a single doctor (Monday 9-12,
 * Monday 2-5, Tuesday 9-12, etc.). The foreign key {@code doctor_id} references
 * {@code doctors.id}. The schema defines {@code ON DELETE CASCADE}, meaning
 * deleting a doctor automatically removes all their availability records.</p>
 *
 * <p>Mapped with {@code @ManyToOne(fetch = FetchType.LAZY)}: loading a
 * DoctorAvailability does not automatically fetch the Doctor. The Doctor is
 * loaded only when {@code getDoctor()} is called within a transaction.</p>
 */
@Entity
@Table(
        name = "doctor_availability",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_availability_doctor_day_time",
                        columnNames = {"doctor_id", "day_of_week", "start_time", "end_time"}
                )
        }
)
public class DoctorAvailability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * Many-to-one relationship to Doctor.
     * The foreign key {@code doctor_id} references {@code doctors(id)}.
     * ON DELETE CASCADE is enforced at the database level.
     *
     * <p>LAZY loading: the Doctor is not fetched until {@code getDoctor()} is
     * called. Useful when you need to list availability slots without always
     * needing the doctor's full profile.</p>
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    /**
     * Day of the week (0-6).
     * Typically: 0 = Sunday, 1 = Monday, ..., 6 = Saturday.
     * The schema CHECK constraint enforces values between 0 and 6.
     *
     * <p>Mapped as {@code byte} because TINYINT UNSIGNED (0-255) fits in a
     * signed byte (-128 to 127) for the range 0-6.</p>
     */
    @Column(name = "day_of_week", nullable = false)
    private byte dayOfWeek;

    /**
     * Start time of the availability slot.
     * Stored as MySQL {@code TIME} type, mapped to Java {@code LocalTime}.
     */
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    /**
     * End time of the availability slot.
     * The schema CHECK constraint enforces {@code start_time < end_time}.
     */
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    /**
     * Whether this is a recurring weekly pattern.
     * If true, the availability repeats every week on the same day.
     * If false, the availability may be a one-time exception (effective dates apply).
     *
     * <p>Defaults to true as most availability patterns are recurring.</p>
     */
    @Column(name = "is_recurring", nullable = false)
    private boolean recurring = true;

    /**
     * Optional date from which this availability becomes effective.
     * Can be null for indefinite/recurring availability starting immediately.
     */
    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    /**
     * Optional date until which this availability is effective.
     * Can be null for availability with no end date.
     * CHECK constraint ensures {@code effective_from <= effective_until} when both set.
     */
    @Column(name = "effective_until")
    private LocalDate effectiveUntil;

    /**
     * Database-generated timestamp.
     * Marked insertable=false so MySQL supplies the default
     * CURRENT_TIMESTAMP(3) on INSERT.
     */
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private java.time.LocalDateTime createdAt;

    /**
     * No-arg constructor required by JPA/Hibernate.
     */
    public DoctorAvailability() {
    }

    public DoctorAvailability(Doctor doctor, byte dayOfWeek, LocalTime startTime, LocalTime endTime) {
        this.doctor = doctor;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public Long getId() {
        return id;
    }

    public Doctor getDoctor() {
        return doctor;
    }

    public void setDoctor(Doctor doctor) {
        this.doctor = doctor;
    }

    public byte getDayOfWeek() {
        return dayOfWeek;
    }

    public void setDayOfWeek(byte dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public boolean isRecurring() {
        return recurring;
    }

    public void setRecurring(boolean recurring) {
        this.recurring = recurring;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public LocalDate getEffectiveUntil() {
        return effectiveUntil;
    }

    public void setEffectiveUntil(LocalDate effectiveUntil) {
        this.effectiveUntil = effectiveUntil;
    }

    public java.time.LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Identity based on surrogate primary key when persisted.
     * Transient instances are never equal unless identical.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DoctorAvailability that)) {
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

    /**
     * Deliberately omits {@code doctor} to avoid recursive toString or
     * accidental lazy-load in logs.
     */
    @Override
    public String toString() {
        return "DoctorAvailability{id=" + id
                + ", dayOfWeek=" + dayOfWeek
                + ", startTime=" + startTime
                + ", endTime=" + endTime
                + ", recurring=" + recurring
                + ", effectiveFrom=" + effectiveFrom
                + ", effectiveUntil=" + effectiveUntil
                + ", createdAt=" + createdAt + "}";
    }
}