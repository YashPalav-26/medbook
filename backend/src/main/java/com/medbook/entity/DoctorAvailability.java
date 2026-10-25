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










    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;









    @Column(name = "day_of_week", nullable = false)
    private byte dayOfWeek;





    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;





    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;








    @Column(name = "is_recurring", nullable = false)
    private boolean recurring = true;





    @Column(name = "effective_from")
    private LocalDate effectiveFrom;






    @Column(name = "effective_until")
    private LocalDate effectiveUntil;






    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private java.time.LocalDateTime createdAt;




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