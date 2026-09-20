package com.medbook.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity mapping the {@code specialties} table.
 *
 * <p>Represents a medical specialty (e.g., Cardiology, Orthopedics). Specialties
 * are reference data managed by the admin rather than generated dynamically.</p>
 *
 * <p>No {@code doctors} collection is included here, keeping the relationship
 * unidirectional from Doctor to Specialty. This avoids loading potentially hundreds
 * of Doctor entities just because a Specialty is read, and simplifies the entity
 * lifecycle: Specialty has no awareness of which doctors reference it.</p>
 *
 * <p>A bidirectional relationship would add convenience for queries like "give me
 * all doctors in Cardiology," but Spring Data JPA's {@code findBySpecialtyName(...)}
 * derived query method achieves that without introducing bi-directional mapping
 * complexity.</p>
 */
@Entity
@Table(
        name = "specialties",
        uniqueConstraints = @UniqueConstraint(name = "name", columnNames = "name")
)
public class Specialty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false, length = 100, unique = true)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /**
     * {@code created_at} is {@code NOT NULL DEFAULT CURRENT_TIMESTAMP(3)} in the
     * schema. Marked {@code insertable = false}/{@code updatable = false} so the
     * INSERT omits it and MySQL supplies the default; Flyway owns the schema, so
     * timestamp generation stays in the database.
     */
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * No-arg constructor required by JPA/Hibernate.
     */
    public Specialty() {
    }

    public Specialty(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
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
        if (!(o instanceof Specialty specialty)) {
            return false;
        }
        if (id == null || specialty.id == null) {
            return false;
        }
        return id.equals(specialty.id);
    }

    @Override
    public int hashCode() {
        return id == null ? UUID.randomUUID().hashCode() : id.hashCode();
    }

    @Override
    public String toString() {
        return "Specialty{id=" + id
                + ", name='" + name + "'"
                + ", description='" + description + "'"
                + ", active=" + active
                + ", createdAt=" + createdAt + "}";
    }
}
