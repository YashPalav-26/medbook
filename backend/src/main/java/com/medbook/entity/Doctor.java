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

/**
 * JPA entity mapping the {@code doctors} table.
 *
 * <p>Represents a doctor's professional profile. Each doctor is linked to exactly
 * one User (through a one-to-one relationship) and belongs to exactly one Specialty
 * (through a many-to-one relationship).</p>
 *
 * <h3>Relationship: Doctor → User (One-to-One)</h3>
 * <p>The database enforces {@code user_id UNIQUE}, meaning only one doctor row can
 * reference a given user. This models the real-world constraint: a user account is
 * either a doctor or not, never two doctors at once.</p>
 *
 * <p>Doctor <em>owns</em> the relationship because {@code doctors.user_id} is the
 * foreign key column. The mapping uses {@code @JoinColumn(name = "user_id")} to
 * declare this ownership.</p>
 *
 * <p>The relationship is <em>unidirectional</em> for now: Doctor knows its User,
 * but User has no {@code doctor} field pointing back. This keeps User simple and
 * avoids the question of whether User should also know about its Patient entity
 * (when that is added). A bidirectional relationship would add convenience for
 * "load user, immediately navigate to doctor," but at the cost of coupling the
 * entities and risking accidental lazy-load triggers. The unidirectional design
 * is sufficient: repositories provide {@code findByUserId(...)} when you need the
 * reverse lookup.</p>
 *
 * <h3>Relationship: Doctor → Specialty (Many-to-One)</h3>
 * <p>Many doctors can share the same specialty (e.g., ten Cardiologists), so this
 * is a many-to-one relationship from Doctor's perspective. The foreign key
 * {@code doctors.specialty_id} references {@code specialties.id}.</p>
 *
 * <p>Mapped with {@code @ManyToOne(fetch = FetchType.LAZY)}: loading a Doctor does
 * not immediately query the specialties table. The Specialty is fetched only when
 * {@code getSpecialty()} is called, and only if the persistence context is still
 * open (inside a transaction or inside the scope of an {@code @Transactional} test).
 * LAZY loading avoids the cost of joining specialties for operations that don't
 * need specialty data.</p>
 *
 * <p>Again, the relationship is <em>unidirectional</em>: Specialty has no
 * {@code Set<Doctor> doctors} collection. Spring Data's derived query method
 * {@code findBySpecialtyName(...)} achieves "find all doctors in Cardiology"
 * without introducing a bidirectional mapping.</p>
 */
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

    /**
     * One-to-one relationship to User.
     * {@code user_id} is UNIQUE in the schema, so only one doctor can reference
     * a given user. Doctor owns the relationship through the {@code user_id} FK.
     *
     * <p>LAZY fetch: the User is loaded only when explicitly accessed, not
     * automatically when the Doctor is loaded.</p>
     */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /**
     * Many-to-one relationship to Specialty.
     * Many doctors share one specialty. Doctor owns the relationship through
     * the {@code specialty_id} FK.
     *
     * <p>LAZY fetch: the Specialty is loaded only when explicitly accessed.</p>
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "specialty_id", nullable = false)
    private Specialty specialty;

    @Column(name = "medical_license_number", nullable = false, length = 50, unique = true)
    private String medicalLicenseNumber;

    /**
     * Maps {@code years_experience TINYINT UNSIGNED}.
     *
     * <p>Hibernate infers SMALLINT for {@code short} and INTEGER for {@code int},
     * neither of which matches MySQL's TINYINT. {@link JdbcTypeCode} pins the JDBC
     * type so validation passes. Java {@code int} range 0-255 is more than enough
     * for a year counter (CHECK constraint enforces 0-80).</p>
     */
    @Column(name = "years_experience", nullable = false)
    @JdbcTypeCode(SqlTypes.TINYINT)
    private int yearsExperience;

    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;

    /**
     * Maps {@code consultation_fee DECIMAL(10,2)}.
     * BigDecimal preserves exact decimal precision for currency.
     */
    @Column(name = "consultation_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal consultationFee;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "profile_picture_url", length = 500)
    private String profilePictureUrl;

    /**
     * Database-generated timestamp. Same strategy as User entity: marked
     * {@code insertable = false}/{@code updatable = false} so MySQL supplies
     * the DEFAULT CURRENT_TIMESTAMP(3) on INSERT.
     */
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Database-generated timestamp. Schema has DEFAULT CURRENT_TIMESTAMP(3) but
     * no ON UPDATE trigger, so this reflects the insert time. Application layer
     * will manage updates explicitly when that becomes a requirement.
     */
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    /**
     * No-arg constructor required by JPA/Hibernate.
     */
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

    /**
     * Identity based on surrogate primary key when persisted.
     * Transient instances are never equal unless identical.
     */
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

    /**
     * Deliberately omits {@code user} and {@code specialty} to avoid triggering
     * lazy loads or recursive toString calls.
     */
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
