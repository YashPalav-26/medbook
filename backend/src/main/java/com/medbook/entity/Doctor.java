package com.medbook.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;











































@Entity
@Table(
        name = "doctors",
        uniqueConstraints = {
                @UniqueConstraint(name = "user_id", columnNames = "user_id"),
                @UniqueConstraint(name = "medical_license_number", columnNames = "medical_license_number")
        }
)
public class Doctor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;









    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;








    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "specialty_id", nullable = false)
    private Specialty specialty;

    @Column(name = "medical_license_number", nullable = false, length = 50, unique = true)
    private String medicalLicenseNumber;









    @Column(name = "years_experience", nullable = false)
    @JdbcTypeCode(SqlTypes.TINYINT)
    private int yearsExperience;

    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;





    @Column(name = "consultation_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal consultationFee;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "profile_picture_url", length = 500)
    private String profilePictureUrl;






    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;






    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;




    public Doctor() {
    }

    public Doctor(User user, Specialty specialty, String medicalLicenseNumber,
                  int yearsExperience, BigDecimal consultationFee) {
        this.user = user;
        this.specialty = specialty;
        this.medicalLicenseNumber = medicalLicenseNumber;
        this.yearsExperience = yearsExperience;
        this.consultationFee = consultationFee;
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

    public Specialty getSpecialty() {
        return specialty;
    }

    public void setSpecialty(Specialty specialty) {
        this.specialty = specialty;
    }

    public String getMedicalLicenseNumber() {
        return medicalLicenseNumber;
    }

    public void setMedicalLicenseNumber(String medicalLicenseNumber) {
        this.medicalLicenseNumber = medicalLicenseNumber;
    }

    public int getYearsExperience() {
        return yearsExperience;
    }

    public void setYearsExperience(int yearsExperience) {
        this.yearsExperience = yearsExperience;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public BigDecimal getConsultationFee() {
        return consultationFee;
    }

    public void setConsultationFee(BigDecimal consultationFee) {
        this.consultationFee = consultationFee;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getProfilePictureUrl() {
        return profilePictureUrl;
    }

    public void setProfilePictureUrl(String profilePictureUrl) {
        this.profilePictureUrl = profilePictureUrl;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }





    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Doctor doctor)) {
            return false;
        }
        if (id == null || doctor.id == null) {
            return false;
        }
        return id.equals(doctor.id);
    }

    @Override
    public int hashCode() {
        return id == null ? UUID.randomUUID().hashCode() : id.hashCode();
    }





    @Override
    public String toString() {
        return "Doctor{id=" + id
                + ", medicalLicenseNumber='" + medicalLicenseNumber + "'"
                + ", yearsExperience=" + yearsExperience
                + ", consultationFee=" + consultationFee
                + ", active=" + active
                + ", profilePictureUrl='" + profilePictureUrl + "'"
                + ", createdAt=" + createdAt
                + ", updatedAt=" + updatedAt + "}";
    }
}
