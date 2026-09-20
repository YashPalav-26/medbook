package com.medbook.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * JPA entity mapping the {@code patients} table.
 *
 * <p>Represents a patient's medical profile and demographics. Each patient is linked
 * to exactly one User (through a one-to-one relationship). The entity stores medical
 * history (allergies, insurance information) and contact details needed for
 * appointment scheduling and care coordination.</p>
 *
 * <h3>Relationship: Patient → User (One-to-One)</h3>
 * <p>The database enforces {@code user_id UNIQUE}, meaning only one patient row can
 * reference a given user. This models the real-world constraint: a user account is
 * either a patient or not, never two patients at once.</p>
 *
 * <p>Patient <em>owns</em> the relationship because {@code patients.user_id} is the
 * foreign key column. The mapping uses {@code @JoinColumn(name = "user_id")} to
 * declare this ownership.</p>
 *
 * <p>The relationship is <em>unidirectional</em> for now: Patient knows its User,
 * but User has no {@code patient} field pointing back. This keeps User focused on
 * authentication concerns and avoids coupling. The unidirectional design is sufficient:
 * repositories provide {@code findByUserId(...)} when you need the reverse lookup.</p>
 */
@Entity
@Table(
        name = "patients",
        uniqueConstraints = @UniqueConstraint(name = "user_id", columnNames = "user_id")
)
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * One-to-one relationship to User.
     * {@code user_id} is UNIQUE in the schema, so only one patient can reference
     * a given user. Patient owns the relationship through the {@code user_id} FK.
     *
     * <p>LAZY fetch: the User is loaded only when explicitly accessed, not
     * automatically when the Patient is loaded.</p>
     */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /**
     * Patient's date of birth. NOT NULL, required for age calculations and
     * medical eligibility checks.
     */
    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    /**
     * First line of patient's address (street address, building, etc).
     * Optional for now, may become required in future phases.
     */
    @Column(name = "address_line1", length = 255)
    private String addressLine1;

    /**
     * Second line of patient's address (apartment, suite, etc).
     * Optional.
     */
    @Column(name = "address_line2", length = 255)
    private String addressLine2;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "state_province", length = 50)
    private String stateProvince;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "country", length = 50)
    private String country;

    /**
     * Emergency contact name. Used for notifications and next-of-kin purposes.
     * Optional for now.
     */
    @Column(name = "emergency_contact_name", length = 200)
    private String emergencyContactName;

    /**
     * Emergency contact phone number. Follows the same format as user phone numbers.
     * Optional for now.
     */
    @Column(name = "emergency_contact_phone", length = 20)
    private String emergencyContactPhone;

    /**
     * Whether the patient has insurance coverage.
     * Defaults to false.
     */
    @Column(name = "has_insurance", nullable = false)
    private boolean hasInsurance = false;

    /**
     * Name of the insurance provider (e.g., "Blue Cross", "Aetna").
     * Can be null if has_insurance = false.
     */
    @Column(name = "insurance_provider", length = 100)
    private String insuranceProvider;

    /**
     * Insurance policy number or member ID.
     * Can be null if has_insurance = false.
     */
    @Column(name = "insurance_policy_number", length = 50)
    private String insurancePolicyNumber;

    /**
     * Known allergies and adverse reactions. Used for clinical decision-making
     * and to prevent medication conflicts.
     * Optional, can be null if patient has no known allergies.
     */
    @Column(name = "allergies", columnDefinition = "TEXT")
    private String allergies;

    /**
     * Whether the patient account is active. Soft deletion pattern.
     * Defaults to true.
     */
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /**
     * Database-generated timestamp. Marked insertable=false so MySQL supplies
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
    public Patient() {
    }

    public Patient(User user, LocalDate dateOfBirth) {
        this.user = user;
        this.dateOfBirth = dateOfBirth;
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

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getAddressLine1() {
        return addressLine1;
    }

    public void setAddressLine1(String addressLine1) {
        this.addressLine1 = addressLine1;
    }

    public String getAddressLine2() {
        return addressLine2;
    }

    public void setAddressLine2(String addressLine2) {
        this.addressLine2 = addressLine2;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getStateProvince() {
        return stateProvince;
    }

    public void setStateProvince(String stateProvince) {
        this.stateProvince = stateProvince;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getEmergencyContactName() {
        return emergencyContactName;
    }

    public void setEmergencyContactName(String emergencyContactName) {
        this.emergencyContactName = emergencyContactName;
    }

    public String getEmergencyContactPhone() {
        return emergencyContactPhone;
    }

    public void setEmergencyContactPhone(String emergencyContactPhone) {
        this.emergencyContactPhone = emergencyContactPhone;
    }

    public boolean hasInsurance() {
        return hasInsurance;
    }

    public void setHasInsurance(boolean hasInsurance) {
        this.hasInsurance = hasInsurance;
    }

    public String getInsuranceProvider() {
        return insuranceProvider;
    }

    public void setInsuranceProvider(String insuranceProvider) {
        this.insuranceProvider = insuranceProvider;
    }

    public String getInsurancePolicyNumber() {
        return insurancePolicyNumber;
    }

    public void setInsurancePolicyNumber(String insurancePolicyNumber) {
        this.insurancePolicyNumber = insurancePolicyNumber;
    }

    public String getAllergies() {
        return allergies;
    }

    public void setAllergies(String allergies) {
        this.allergies = allergies;
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
        if (!(o instanceof Patient patient)) {
            return false;
        }
        if (id == null || patient.id == null) {
            return false;
        }
        return id.equals(patient.id);
    }

    @Override
    public int hashCode() {
        return id == null ? UUID.randomUUID().hashCode() : id.hashCode();
    }

    /**
     * Deliberately omits {@code user} to avoid triggering lazy loads or
     * recursive toString calls.
     */
    @Override
    public String toString() {
        return "Patient{id=" + id
                + ", dateOfBirth=" + dateOfBirth
                + ", city='" + city + "'"
                + ", hasInsurance=" + hasInsurance
                + ", active=" + active
                + ", createdAt=" + createdAt
                + ", updatedAt=" + updatedAt + "}";
    }
}
