package com.medbook.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * JPA entity mapping the {@code roles} table.
 *
 * <p>Represents an RBAC role (ADMIN, DOCTOR, PATIENT). Role rows are seeded by the
 * Flyway V1 migration and treated as immutable reference data.</p>
 *
 * <p>This is the inverse side of the {@code user_roles} many-to-many relationship;
 * the owning side lives on {@link User}, so no join-table metadata appears here.</p>
 */
@Entity
@Table(
        name = "roles",
        uniqueConstraints = @UniqueConstraint(name = "name", columnNames = "name")
)
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false, length = 50, unique = true)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    /**
     * Inverse side of User&#8594;Role. Kept lazy and read-only from here to avoid
     * accidentally triggering loads or cascade writes through this direction.
     */
    @ManyToMany(mappedBy = "roles")
    private Set<User> users = new HashSet<>();

    /**
     * No-arg constructor required by JPA/Hibernate. Package-private would suffice,
     * but public is kept for clarity; application code should use the natural
     * constructor below.
     */
    public Role() {
    }

    public Role(String name, String description) {
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

    public Set<User> getUsers() {
        return users;
    }

    /**
     * Identity is based on the surrogate primary key when persisted.
     * For transient (unsaved) instances, a random UUID keeps hash-based
     * collections consistent until an id is assigned.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Role role)) {
            return false;
        }
        if (id == null || role.id == null) {
            // Two unpersisted entities are never equal unless identical
            return false;
        }
        return id.equals(role.id);
    }

    @Override
    public int hashCode() {
        // A stable UUID while unsaved prevents hash-code changes after persist
        return id == null ? UUID.randomUUID().hashCode() : id.hashCode();
    }

    /**
     * Deliberately omits {@code users} to avoid recursive toString calls
     * across the many-to-many relationship.
     */
    @Override
    public String toString() {
        return "Role{id=" + id + ", name='" + name + "', description='" + description + "'}";
    }
}
