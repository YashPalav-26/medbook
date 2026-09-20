package com.medbook.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * JPA entity mapping the {@code users} table.
 *
 * <p>Holds authentication and account data only. Doctor- and patient-specific
 * data lives in separate tables that reference this one.</p>
 *
 * <p>Owns the {@code user_roles} many-to-many relationship to {@link Role}.</p>
 */
@Entity
@Table(
        name = "users",
        uniqueConstraints = @UniqueConstraint(name = "email", columnNames = "email")
)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "email", nullable = false, length = 255, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "phone_number", nullable = false, length = 20)
    private String phoneNumber;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "is_locked", nullable = false)
    private boolean locked = false;

    /**
     * Maps the {@code failed_login_attempts TINYINT UNSIGNED} column.
     *
     * <p>Hibernate infers {@code SMALLINT} for {@code short} and {@code INTEGER}
     * for {@code int}, neither of which matches MySQL's {@code TINYINT} JDBC type,
     * which would make {@code ddl-auto=validate} fail. {@link JdbcTypeCode}
     * pins the JDBC type explicitly so a plain Java {@code int} (range 0-255 is
     * far more than enough for a login-attempt counter) maps to the existing
     * column without touching the schema.</p>
     */
    @Column(name = "failed_login_attempts", nullable = false)
    @JdbcTypeCode(SqlTypes.TINYINT)
    private int failedLoginAttempts = 0;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    /**
     * {@code created_at} is {@code NOT NULL DEFAULT CURRENT_TIMESTAMP(3)} in the
     * schema. Marked {@code insertable = false}/{@code updatable = false} so the
     * INSERT omits it and MySQL supplies the default; Flyway owns the schema, so
     * timestamp generation stays in the database rather than being duplicated in
     * Java. Read it back from the persistence context after a flush/refresh.
     */
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Same treatment as {@link #createdAt}: populated by the MySQL default.
     * Note the V1 schema has no {@code ON UPDATE} trigger, so this reflects the
     * insert timestamp; the application layer will manage it explicitly when
     * that becomes a real requirement.
     */
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    /**
     * Roles granted to this user. Backed by the {@code user_roles} join table:
     * joinColumns = the FK pointing back to this user (user_id),
     * inverseJoinColumns = the FK pointing to the role (role_id).
     *
     * <p>Lazy by default so loading a user does not hit the join table unless
     * roles are actually read. A {@link Set} is used because roles are unordered
     * and must be distinct.</p>
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    /**
     * No-arg constructor required by JPA/Hibernate.
     */
    public User() {
    }

    public User(String email, String passwordHash, String firstName, String lastName, String phoneNumber) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.firstName = firstName;
        this.lastName = lastName;
        this.phoneNumber = phoneNumber;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public void setFailedLoginAttempts(int failedLoginAttempts) {
        this.failedLoginAttempts = failedLoginAttempts;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public void setRoles(Set<Role> roles) {
        this.roles = roles;
    }

    /**
     * Convenience helper to add a role without risking duplicate entries.
     * (Simple field-level helper; no bidirectional consistency logic yet.)
     */
    public void addRole(Role role) {
        roles.add(role);
    }

    /**
     * Identity is based on the surrogate primary key when persisted.
     * For transient (unsaved) instances, hashCode stability is preserved so
     * hash-based collections behave before and after the id is assigned.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof User user)) {
            return false;
        }
        if (id == null || user.id == null) {
            // Two unpersisted entities are never equal unless identical
            return false;
        }
        return id.equals(user.id);
    }

    @Override
    public int hashCode() {
        return id == null ? UUID.randomUUID().hashCode() : id.hashCode();
    }

    /**
     * Deliberately omits {@code roles} (would risk recursive toString across the
     * many-to-many relationship) and {@code passwordHash} (never logged).
     */
    @Override
    public String toString() {
        return "User{id=" + id
                + ", email='" + email + "'"
                + ", firstName='" + firstName + "'"
                + ", lastName='" + lastName + "'"
                + ", phoneNumber='" + phoneNumber + "'"
                + ", active=" + active
                + ", locked=" + locked
                + ", failedLoginAttempts=" + failedLoginAttempts
                + ", lastLoginAt=" + lastLoginAt
                + ", createdAt=" + createdAt
                + ", updatedAt=" + updatedAt + "}";
    }
}
