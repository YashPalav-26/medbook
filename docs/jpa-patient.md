# JPA Patient Domain

## What Patient Entity Represents

Patient maps to the `patients` table which stores a patient's medical profile and demographic information. It contains:

- **Demographics**: Date of birth, address details, contact information
- **Medical history**: Allergies, insurance information
- **Relationship to User**: Each patient is linked to exactly one User account
- **Administrative data**: Active status, creation/update timestamps

This entity separates medical/patient-specific data from the User entity which handles authentication and basic profile data (email, name, phone).

## How @Entity and @Table Work

```java
@Entity
@Table(
    name = "patients",
    uniqueConstraints = @UniqueConstraint(name = "user_id", columnNames = "user_id")
)
```

- **@Entity**: Marks this class as a JPA entity that will be mapped to a database table
- **@Table**: Specifies the exact table name in the database (`"patients"`)
- **uniqueConstraints**: Declares that the `user_id` column has a UNIQUE constraint in the database, ensuring only one patient can reference a given user

## Fields Mapping to Patients Columns

| Database Column | Java Field | Type | Notes |
|----------------|------------|------|-------|
| `id` | `id` | `Long` | Primary key, auto-increment |
| `user_id` | `user` | `User` | Foreign key relationship |
| `date_of_birth` | `dateOfBirth` | `LocalDate` | NOT NULL, required |
| `address_line1` | `addressLine1` | `String` | Optional address component |
| `address_line2` | `addressLine2` | `String` | Optional address component |
| `city` | `city` | `String` | Optional |
| `state_province` | `stateProvince` | `String` | Optional |
| `postal_code` | `postalCode` | `String` | Optional |
| `country` | `country` | `String` | Optional |
| `emergency_contact_name` | `emergencyContactName` | `String` | Optional |
| `emergency_contact_phone` | `emergencyContactPhone` | `String` | Optional, phone format |
| `has_insurance` | `hasInsurance` | `boolean` | Default false |
| `insurance_provider` | `insuranceProvider` | `String` | Optional, depends on has_insurance |
| `insurance_policy_number` | `insurancePolicyNumber` | `String` | Optional, depends on has_insurance |
| `allergies` | `allergies` | `String` | TEXT column, optional |
| `is_active` | `active` | `boolean` | Default true, soft deletion |
| `created_at` | `createdAt` | `LocalDateTime` | DB-generated timestamp |
| `updated_at` | `updatedAt` | `LocalDateTime` | DB-generated timestamp |

## Patient → User Relationship

The database defines:

```sql
user_id BIGINT UNSIGNED NOT NULL UNIQUE
FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT
```

### Relationship Type: One-to-One

**Why UNIQUE makes it one-to-one:**
- The `UNIQUE` constraint means each `user_id` can appear at most once in the `patients` table
- This models the real-world constraint: one user account represents one person
- A person can be either a patient OR a doctor, not both

### Mapping:

```java
@OneToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "user_id", nullable = false, unique = true)
private User user;
```

- **@OneToOne**: Declares the relationship as one-to-one
- **@JoinColumn**: Specifies the foreign key column (`user_id`) with its constraints
- **nullable = false**: Column is `NOT NULL` in the database
- **unique = true**: Column has `UNIQUE` constraint
- **fetch = FetchType.LAZY**: User is loaded only when explicitly accessed

### Relationship Ownership

Patient **owns** the relationship because:
- The foreign key column `user_id` lives in the `patients` table
- Patient entity has the `@JoinColumn` annotation
- This follows the same pattern as Doctor → User relationship

### Unidirectional Design

The relationship is unidirectional: Patient knows its User, but User has no `patient` field pointing back. This:

- **Keeps User focused** on authentication concerns
- **Avoids coupling** between User, Doctor, and Patient entities
- **Works with repository methods** like `findByUserId()` for reverse lookups
- **Matches the Doctor → User pattern** established earlier

If we need to find a Patient from a User, we use:
```java
Optional<Patient> patient = patientRepository.findByUserId(userId);
```

## Why FetchType.LAZY is Used

```java
fetch = FetchType.LAZY
```

**EAGER loading** (default for `@OneToOne`):
- When a Patient is loaded, the User is fetched immediately with a JOIN
- Works without a transaction context
- Always queries both tables, even if we don't need User data

**LAZY loading**:
- When a Patient is loaded, the User is NOT fetched
- User is loaded only when `getUser()` is called
- Requires an open transaction/persistence context
- Example:

```java
@Transactional
void showPatientDetails(Long patientId) {
    Patient patient = patientRepository.findById(patientId).orElseThrow();
    // User NOT loaded yet
    
    User user = patient.getUser(); // Query fires HERE
    System.out.println(user.getEmail());
}
```

**Why LAZY is appropriate:**
- Many patient queries don't need User data (list views, reports)
- Reduces unnecessary JOIN overhead
- Works well with `@Transactional` repository tests
- Consistent with Doctor entity's relationship pattern

**Important**: Outside a transaction, accessing a lazy relationship throws `LazyInitializationException`. Always access lazy relationships inside a transactional context or ensure the persistence context is still open.

## Repository Methods and Derived Queries

### PatientRepository Interface

```java
public interface PatientRepository extends JpaRepository<Patient, Long> {
    Optional<Patient> findByUserId(Long userId);
    List<Patient> findByActiveTrue();
}
```

### How findByUserId Works

Spring Data JPA generates:

```sql
SELECT * FROM patients WHERE user_id = ?
```

- Uses the property name `userId` which corresponds to `Patient.user.id`
- Returns `Optional<Patient>` because `user_id` is UNIQUE (0 or 1 result)
- Used for reverse lookups from User to Patient

### How findByActiveTrue Works

Spring Data JPA generates:

```sql
SELECT * FROM patients WHERE is_active = true
```

- The method name `findByActiveTrue` is parsed as:
  - `findBy`: Start of query
  - `Active`: Property name (`active`)
  - `True`: Filter where property equals `true`
- Useful for queries that should exclude soft-deleted patients

## Important Java/MySQL Type Mappings

| MySQL Type | Java Type | Notes |
|------------|-----------|-------|
| `BIGINT UNSIGNED` | `Long` | Primary key, consistent with other entities |
| `DATE` | `LocalDate` | Date without timezone, for birth dates |
| `VARCHAR` | `String` | Variable-length text |
| `TEXT` | `String` | With `columnDefinition = "TEXT"` |
| `BOOLEAN` | `boolean` | Direct mapping |
| `DATETIME(3)` | `LocalDateTime` | With `insertable = false, updatable = false` |

### Database-Generated Timestamps

Columns `created_at` and `updated_at` are defined as:

```sql
created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
```

Entity mapping:

```java
@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
private LocalDateTime createdAt;

@Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
private LocalDateTime updatedAt;
```

- **`insertable = false`**: Hibernate omits these columns from INSERT
- **`updatable = false`**: Hibernate omits them from UPDATE
- MySQL supplies the default values (`CURRENT_TIMESTAMP(3)`)
- The schema has no `ON UPDATE` trigger, so `updated_at` reflects insert time
- Application layer will manage `updated_at` updates when needed

## Transactional Repository Tests

### Test Strategy

```java
@SpringBootTest
@Transactional
class PatientRepositoryTest {
    // Tests here
}
```

### How Tests Work

1. **@SpringBootTest**: Boots the full Spring application context
2. **@Transactional**: Wraps each test method in a transaction
3. **Rollback behavior**: After test execution, transaction rolls back
4. **No permanent data**: Test patients/users are removed automatically

### Test Coverage

The tests verify:

| Test | Verifies |
|------|----------|
| `save/findById` | CRUD operations work |
| `findByUserId` | User relationship lookup |
| `findByActiveTrue` | Boolean filter works |
| `getUser()` | LAZY relationship loads inside transaction |
| Timestamps | DB defaults populate `created_at`/`updated_at` |
| Optional fields | Address, contact, insurance fields work |
| Soft deletion | Active/inactive flag behavior |

### Test Data Management

Tests create users with the `PATIENT` role:

```java
private User createTestPatientUser(String email) {
    Role patientRole = roleRepository.findByName("PATIENT").orElseThrow();
    User user = new User(email, "hashed-password", "Test", "Patient", "+919876543211");
    user.addRole(patientRole);
    return userRepository.save(user);
}
```

This:
- Uses seeded PATIENT role (from Flyway V1 migration)
- Creates unique email for each test
- Rolls back automatically via `@Transactional`

## Learning Summary

**What we implemented:**

1. **Entity mapping** - `@Entity` + `@Table(name = "patients")`
2. **Column mapping** - `@Column` with exact database column names
3. **One-to-one relationship** - Patient → User with `@OneToOne`
4. **Foreign key constraints** - `@JoinColumn(name = "user_id", nullable = false, unique = true)`
5. **Lazy loading** - `fetch = FetchType.LAZY` for performance
6. **Database-generated timestamps** - `insertable = false, updatable = false`
7. **Repository methods** - Derived queries with Spring Data JPA
8. **Transactional tests** - Rollback behavior prevents permanent test data

**Key concepts:**
- UNIQUE foreign key → one-to-one relationship
- Patient owns the relationship (FK column in patients table)
- LAZY loading reduces JOIN overhead
- Derived query methods generate SQL based on method names
- `@Transactional` tests clean up automatically

**Consistency with existing code:**
- Follows same patterns as User, Role, Doctor, Specialty entities
- Consistent timestamp strategy (DB-generated defaults)
- Same repository test structure
- Unified approach to relationships (unidirectional from child to parent)