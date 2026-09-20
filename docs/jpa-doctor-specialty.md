# JPA Doctor + Specialty Domain

## Why Specialty is an Entity

Specialty maps to the `specialties` table which stores medical specialties like "Cardiology", "Dermatology", etc. It is an entity because:

1. **Reference data managed administratively** — Admins create and maintain specialties
2. **Has its own lifecycle** — Can be activated/deactivated without touching other entities
3. **Referenced by doctors** — The `doctors.specialty_id` foreign key references it
4. **Has attributes beyond a simple name** — includes description, active flag, timestamps

A `VARCHAR(100)` in a lookup table would suffice for simple cases, but as an entity Specialty can:
- Be queried independently (`findByName`, `findByNameIgnoreCase`)
- Have its description updated
- Track activation state
- Participate in relationships

## Why Doctor is an Entity

Doctor maps to the `doctors` table which stores doctor-specific information beyond what `users` provides:

1. **Professional credentials** — License number, years of experience, consultation fee
2. **Medical specialty** — Which field the doctor practices in
3. **Profile information** — Bio, profile picture URL
4. **Active status** — Can be activated/deactivated independently of the user account

The `users` table holds authentication and basic profile data (email, name, phone). Doctor adds the professional layer specific to healthcare providers.

## Why Doctor → User is One-to-One

The database schema defines:

```sql
doctors.user_id BIGINT UNSIGNED NOT NULL UNIQUE
```

The `UNIQUE` constraint means each `user_id` can appear at most once in the `doctors` table. This models the real-world constraint:

- **A user account represents ONE person**
- **That person is EITHER a doctor, OR a patient, OR an admin**
- **A person cannot be two doctors simultaneously**

The relationship is owned by Doctor (the `@JoinColumn` lives on Doctor), meaning:
- The foreign key column `user_id` lives in the `doctors` table
- To find a Doctor from a User, use `DoctorRepository.findByUserId(userId)`
- User has no `@OneToOne` back-reference in this implementation

## Why Doctor → Specialty is Many-to-One

The schema:

```sql
doctors.specialty_id BIGINT UNSIGNED NOT NULL
specialties.id BIGINT UNSIGNED PRIMARY KEY
```

Specialties are shared across multiple doctors. The conceptual model:

```
Specialty (1)
    ↓
    many Doctors
```

Many doctors share one specialty (ten Cardiologists), making it a **many-to-one** from Doctor's perspective. The relationship is:

- **Owned by Doctor** — Foreign key lives in `doctors` table
- **Lazy loaded** — `FetchType.LAZY` avoids joining specialties for every doctor query
- **Optional = false** — Every doctor must have a specialty

## What @JoinColumn Does

```java
@JoinColumn(name = "user_id", nullable = false, unique = true)
private User user;
```

`@JoinColumn` specifies:
- **`name`** — The foreign key column name in the child table (`doctors.user_id`)
- **`nullable = false`** — The column has `NOT NULL` constraint
- **`unique = true`** — The column has `UNIQUE` constraint (makes it one-to-one)

Without `@JoinColumn`, JPA uses a default name like `user_id` anyway, but explicit declaration makes the constraint intent clear.

## What FetchType.LAZY Means

```java
@ManyToOne(fetch = FetchType.LAZY)
private Specialty specialty;
```

**EAGER loading** (default for `@ManyToOne`):
- When a Doctor is loaded, the specialty is fetched immediately with a JOIN
- Works without a transaction, but always queries both tables

**LAZY loading**:
- When a Doctor is loaded, the specialty is NOT fetched
- Specialty is loaded later, only when `getSpecialty()` is called
- Requires an open transaction/persistence context
- Example:

```java
@Transactional
void showSpecialty(Long doctorId) {
    Doctor doc = doctorRepository.findById(doctorId).orElseThrow();
    // specialty NOT loaded yet

    Specialty s = doc.getSpecialty(); // Query fires HERE
    System.out.println(s.getName());
}
```

**Why LAZY is appropriate here:**
- Many doctor queries don't need specialty data (list of all doctors with pagination)
- Loads only what is accessed
- Reduces unnecessary JOINs
- Works well with `@Transactional` test methods

**Caveat:** Outside a transaction, accessing a lazy collection throws `LazyInitializationException`.

## Why Unidirectional Relationships

### Current Design

```
User
  ↑
  │ one-to-one
  │
Doctor
  │
  │ many-to-one
  ↓
Specialty
```

Specialty has no `Set<Doctor>` collection.
User has no `Doctor` reference.

### Why This Approach

1. **Specialty doesn't need to know its doctors**
   - "Find all doctors in Cardiology" is done via `doctorRepository.findBySpecialtyName("Cardiology")`
   - No collection to maintain, no cascade operations

2. **User keeps authentication concerns separate**
   - User entity focuses on auth (email, password, roles)
   - Doctor entity holds professional data
   - Cleaner separation of concerns

3. **Avoids bidirectional complexity**
   - No need for `mappedBy` on one side
   - No synchronization logic (add/remove from both sides)
   - Simpler entity lifecycle

4. **Performance predictable**
   - Loading a Specialty never triggers Doctor queries
   - Loading a User never triggers Doctor queries

### When Bidirectional Makes Sense

- When you frequently need "give me all X's children" from the parent side
- When you need to navigate both directions in the same operation
- When the relationship is truly part of the parent's identity

For Doctor-Specialty, the query pattern is "doctors by specialty," not "specialty by doctors," so unidirectional is sufficient.

## How findBySpecialtyName Works

```java
List<Doctor> findBySpecialtyName(String specialtyName);
```

Spring Data JPA generates this SQL:

```sql
SELECT d.* FROM doctors d
INNER JOIN specialties s ON d.specialty_id = s.id
WHERE s.name = ?
```

The method name is parsed as:
- `findBy` — Start of query
- `Specialty` — Navigate to the `specialty` property (entity field)
- `Name` — On that entity, find the `name` property

Spring handles the JOIN automatically. This is called **nested property traversal**.

## How findByActiveTrue Works

```java
List<Doctor> findByActiveTrue();
```

Spring Data JPA generates:

```sql
SELECT * FROM doctors WHERE is_active = true
```

The `True` suffix filters where the boolean property equals `true`. Similarly:
- `findByActiveFalse()` — WHERE is_active = false

This is shorthand for `findByActive(boolean active)` called with `true`.

## How findByUserId Works

```java
Optional<Doctor> findByUserId(Long userId);
```

Generates:

```sql
SELECT * FROM doctors WHERE user_id = ?

-- With unique constraint, returns at most one row
```

Because `user_id` is UNIQUE in the schema, this query will return zero or one result, making `Optional<Doctor>` the correct return type.

## Java/MySQL Type Mappings

| MySQL Type | Java Type | Notes |
|------------|-----------|-------|
| `BIGINT UNSIGNED` | `Long` | Primary key |
| `VARCHAR` | `String` | Variable-length text |
| `TEXT` | `String` | With `columnDefinition = "TEXT"` |
| `BOOLEAN` | `boolean` | Mapped directly |
| `DECIMAL(10,2)` | `BigDecimal` | Precision for currency |
| `DATETIME(3)` | `LocalDateTime` | With `insertable = false` |
| `TINYINT UNSIGNED` | `int` | With `@JdbcTypeCode(SqlTypes.TINYINT)` |

For `TINYINT UNSIGNED`:
- Hibernate defaults `int` to `INTEGER` and `short` to `SMALLINT`
- Neither matches MySQL's `TINYINT`
- `@JdbcTypeCode(SqlTypes.TINYINT)` pins the JDBC type explicitly
- Java `int` range (0-255) covers `TINYINT` (0-255) comfortably

## Conceptual Model

```
┌─────────────────────────────────────────────────────┐
│                     User                             │
│  id, email, name, phone, active, roles...           │
└───────────────────┬─────────────────────────────────┘
                    │ one-to-one (user_id UNIQUE)
                    ↓
┌─────────────────────────────────────────────────────┐
│                    Doctor                            │
│  id, user, specialty, license, fee, bio, active...  │
└───────────────────┬─────────────────────────────────┘
                    │ many-to-one
                    ↓
┌─────────────────────────────────────────────────────┐
│                   Specialty                          │
│  id, name, description, active, createdAt           │
└─────────────────────────────────────────────────────┘
```

**Key points:**
- Doctor owns both relationships (FK columns are in `doctors`)
- User ↔ Doctor is one-to-one through `user_id UNIQUE`
- Doctor ↔ Specialty is many-to-one (many doctors, one specialty)
- Navigation: Doctor → User, Doctor → Specialty (not the reverse)

## Test Coverage

The repository tests verify:

| Test | Verifies |
|------|----------|
| `findAll` | Query executes against real schema |
| `save/findById` | CRUD round trip |
| `findByName` | Specialty lookup by name |
| `findByNameIgnoreCase` | Case-insensitive lookup |
| `findByMedicalLicenseNumber` | Unique constraint lookup |
| `findBySpecialtyName` | Nested property traversal JOINs |
| `findByActiveTrue` | Boolean filter works |
| `findByUserId` | One-to-one FK lookup |
| `getUser()` | Lazy relationship loads in transaction |
| `getSpecialty()` | Lazy relationship loads in transaction |
| `Timestamps` | DB defaults populate createdAt/updatedAt |
| `BigDecimal` | DECIMAL precision preserved |

All tests are `@Transactional` and roll back — no permanent test data.