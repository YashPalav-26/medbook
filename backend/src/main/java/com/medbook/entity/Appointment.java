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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA entity mapping the {@code appointments} table.
 *
 * <p>Represents a scheduled appointment between a patient and a doctor.
 * Stores the appointment details, current status, and optional confirmation/completion
 * timestamps. The consultation fee is captured as a snapshot to preserve the rate
 * applicable when the appointment was created.</p>
 *
 * <p>Relationships:
 * <ul>
 * <li>Appointment → Doctor: Many-to-one. Many appointments can be scheduled with one doctor.</li>
 * <li>Appointment → Patient: Many-to-one. Many appointments can be scheduled for one patient.</li>
 * <li>Appointment → AppointmentStatusHistory: One-to-many. Each appointment has a history of status changes.</li>
 * </ul>
 * </p>
 */
@Entity
@Table(name = "appointments")
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * Many-to-one relationship to Patient.
     * The foreign key {@code patient_id} references {@code patients(id)}.
     * ON DELETE RESTRICT at database level.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    /**
     * Many-to-one relationship to Doctor.
     * The foreign key {@code doctor_id} references {@code doctors(id)}.
     * ON DELETE RESTRICT at database level.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    /**
     * Date of the appointment.
     */
    @Column(name = "appointment_date", nullable = false)
    private LocalDate appointmentDate;

    /**
     * Start time of the appointment. CHECK constraint enforces start_time < end_time.
     */
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    /**
     * End time of the appointment.
     */
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    /**
     * Reason for the appointment (chief complaint, procedure type, etc).
     * Optional.
     */
    @Column(name = "reason", length = 500)
    private String reason;

    /**
     * Additional notes about the appointment.
     * Optional.
     */
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    /**
     * Current status of the appointment.
     * Stored as VARCHAR(20) in the database, mapped to Java enum.
     * Defaults to SCHEDULED.
     */
    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private AppointmentStatus status = AppointmentStatus.SCHEDULED;

    /**
     * Reason for cancellation, if the appointment was cancelled.
     * CHECK constraint enforces that if status='CANCELLED', cancellation_reason IS NOT NULL.
     * Optional when status != CANCELLED.
     */
    @Column(name = "cancellation_reason", length = 255)
    private String cancellationReason;

    /**
     * Consultation fee applicable at the time this appointment was created.
     * Preserved as a snapshot to maintain historical accuracy of appointment records.
     * CHECK constraint enforces consultation_fee_snapshot >= 0.
     */
    @Column(name = "consultation_fee_snapshot", nullable = false, precision = 10, scale = 2)
    private BigDecimal consultationFeeSnapshot;

    /**
     * Database-generated timestamp. Marked insertable=false so MySQL supplies
     * the DEFAULT CURRENT_TIMESTAMP(3) on INSERT.
     */
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Database-generated timestamp. Same strategy as created_at.
     */
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    /**
     * Timestamp when the appointment was confirmed.
     * Optional, initially null until status changes to CONFIRMED.
     */
    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    /**
     * Timestamp when the appointment was completed.
     * Optional, initially null until status changes to COMPLETED.
     */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /**
     * One-to-many relationship to AppointmentStatusHistory.
     * Each appointment has a history of status changes (audit trail).
     * mappedBy tells JPA that the inverse side (AppointmentStatusHistory.appointment) owns the relationship.
     */
    @OneToMany(mappedBy = "appointment", fetch = FetchType.LAZY)
    private List<AppointmentStatusHistory> statusHistory = new ArrayList<>();

    /**
     * No-arg constructor required by JPA/Hibernate.
     */
    public Appointment() {
    }

    public Appointment(Patient patient, Doctor doctor, LocalDate appointmentDate,
                       LocalTime startTime, LocalTime endTime, BigDecimal consultationFeeSnapshot) {
        this.patient = patient;
        this.doctor = doctor;
        this.appointmentDate = appointmentDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.consultationFeeSnapshot = consultationFeeSnapshot;
    }

    public Long getId() {
        return id;
    }

    public Patient getPatient() {
        return patient;
    }

    public void setPatient(Patient patient) {
        this.patient = patient;
    }

    public Doctor getDoctor() {
        return doctor;
    }

    public void setDoctor(Doctor doctor) {
        this.doctor = doctor;
    }

    public LocalDate getAppointmentDate() {
        return appointmentDate;
    }

    public void setAppointmentDate(LocalDate appointmentDate) {
        this.appointmentDate = appointmentDate;
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

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public void setStatus(AppointmentStatus status) {
        this.status = status;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public void setCancellationReason(String cancellationReason) {
        this.cancellationReason = cancellationReason;
    }

    public BigDecimal getConsultationFeeSnapshot() {
        return consultationFeeSnapshot;
    }

    public void setConsultationFeeSnapshot(BigDecimal consultationFeeSnapshot) {
        this.consultationFeeSnapshot = consultationFeeSnapshot;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public LocalDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(LocalDateTime confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public List<AppointmentStatusHistory> getStatusHistory() {
        return statusHistory;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Appointment appointment)) {
            return false;
        }
        if (id == null || appointment.id == null) {
            return false;
        }
        return id.equals(appointment.id);
    }

    @Override
    public int hashCode() {
        return id == null ? UUID.randomUUID().hashCode() : id.hashCode();
    }

    @Override
    public String toString() {
        return "Appointment{id=" + id
                + ", appointmentDate=" + appointmentDate
                + ", startTime=" + startTime
                + ", endTime=" + endTime
                + ", status=" + status
                + ", consultationFeeSnapshot=" + consultationFeeSnapshot
                + ", createdAt=" + createdAt
                + ", updatedAt=" + updatedAt + "}";
    }
}
