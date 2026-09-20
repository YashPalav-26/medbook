# JPA Doctor Availability Domain

## What DoctorAvailability Represents

DoctorAvailability maps to the `doctor_availability` table which stores weekly time slots when doctors can accept appointments. This is a **configuration table**, not an appointment record.

### Purpose

The entity defines:
- **When** a doctor is available (day of week + time range)
- **Whether** the availability is recurring or a one-time exception
- **When** the availability is effective (optional date range)

The `appointments` table stores actual booked appointments. DoctorAvailability defines the "availability windows" that appointments can be booked into.

## Why a Separate Table/Entity

### Separation of Concerns

| Concern | Table | Purpose |
|---------|-------|---------|
| Authentication | `users` | Login credentials, account status |
| Medical Profile | `patients` | Demographics, insurance, allergies |
| Professional Info | `doctors` | License, fee, specialty |
| **Availability** | `doctor_availability` | **When doctor can accept appointments** |
| **Appointments** | `appointments` | **Actual booked appointments** |

### Data Modeling

A doctor's availability is configuration data (Monday 9-12, Wednesday 2-5).
An appointment is a transactional record (John Doe booked Monday 9:30).

These are different data domains with different lifecycles:
- Availability changes rarely (doctor updates schedule monthly)
- Appointments are created daily by patients

## DoctorAvailability → Doctor Relationship

The database schema defines:

```sql
doctor_id BIGINT UNSIGNED NOT NULL,
FOREIGN KEY (doctor_id) REFERENCES doctors(id) ON DELETE CASCADE
```

### Why Many-to-One

**Many** availability records → **One** doctor

Example:
```
Doctor Jones
    ├─ Monday 9:00-12:00
    ├─ Monday 14:00-17:00
    ├─ Wednesday 9:00-12:00
    └─ Friday 10:00-14:00
```

Each availability slot references the same doctor. The foreign key `doctor_id` is NOT unique, so many availability records can reference one doctor.

### Mapping

```java
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "doctor_id", nullable = false)
private Doctor doctor;
```

- **@ManyToOne**: Many availability records can reference one doctor
- **@JoinColumn(name = "doctor_id")**: The FK column name in the availability table
- **nullable = false**: Schema enforces NOT NULL
- **fetch = FetchType.LAZY**: Avoid loading doctor when listing availability slots

### Why LAZY Loading

Loading availability slots often happens in contexts where the doctor's profile isn't needed:

```java
// Show all availability slots for a month - doctor profile not needed
List<DoctorAvailability> slots = availabilityRepository.findAll();
```

LAZY loading:
- Only fetches doctor when `getDoctor()` is explicitly called
- Reduces unnecessary JOIN overhead
- Works well with transactional tests

## Day/Time Java Mappings

### Day of Week

**MySQL**: `TINYINT UNSIGNED` with CHECK constraint `BETWEEN 0 AND 6`

**Java**: `byte` mapped as `@Column(name = "day_of_week")`

- TINYINT UNSIGNED range: 0-255
- We only use 0-6 (Sunday-Saturday)
- Java `byte` range: -128 to 127 (sufficient for 0-6)

**Conventional mapping**:
```
0 = Sunday
1 = Monday
2 = Tuesday
3 = Wednesday
4 = Thursday
5 = Friday
6 = Saturday
```

### Time Fields

**MySQL**: `TIME NOT NULL`

**Java**: `LocalTime` mapped as `@Column(name = "start_time")` and `@Column(name = "end_time")`

- `TIME` type stores time of day (HH:MM:SS)
- `LocalTime` is the Java equivalent
- No timezone needed (availability is same regardless of timezone)

### Date Fields

**MySQL**: `DATE` (optional)

**Java**: `LocalDate`

Used for `effective_from` and `effective_until` when availability is not indefinite/recurring.

## Active Flag

The schema does NOT have an `is_active` flag in the `doctor_availability` table. Availability slots are either:
- **Active** (within effective date range, if specified)
- **Inactive** (outside effective date range)

This is different from Doctor/Patient entities which have an `is_active` boolean column.

## Repository Derived Queries

### DoctorAvailabilityRepository

```java
List<DoctorAvailability> findByDoctorId(Long doctorId);
List<DoctorAvailability> findByDoctorIdAndDayOfWeek(Long doctorId, byte dayOfWeek);
```

### How findByDoctorId Works

Spring Data JPA generates:

```sql
SELECT * FROM doctor_availability WHERE doctor_id = ?
```

Returns all availability slots for a doctor, regardless of day or time.

**Use case**: Show doctor's complete availability schedule.

### How findByDoctorIdAndDayOfWeek Works

Spring Data JPA generates:

```sql
SELECT * FROM doctor_availability 
WHERE doctor_id = ? AND day_of_week = ?
```

Returns all availability slots for a doctor on a specific day.

**Use case**: "What hours is Dr. Jones available on Mondays?"

## Transactional Tests

### Test Strategy

```java
@SpringBootTest
@Transactional
class DoctorAvailabilityRepositoryTest {
    // Tests here
}
```

### How Tests Work

1. **@SpringBootTest**: Boots full Spring context with MySQL
2. **@Transactional**: Wraps each test in a transaction
3. **Rollback**: Test data removed after test completes
4. **No permanent data**: Tests leave database unchanged

### Test Coverage

Tests verify:
- Save/retrieve availability records
- `findByDoctorId` returns all slots
- `findByDoctorIdAndDayOfWeek` filters by day
- LAZY loading of Doctor relationship
- Timestamps populated by database
- `is_recurring` defaults to true
- Effective date fields work
- Multiple doctors have separate availability
- Delete removes availability

## JPA Validation Against Flyway Schema

With `spring.jpa.hibernate.ddl-auto=validate`, Hibernate validates that:

| Entity Property | Database Column | Match |
|-----------------|-----------------|-------|
| `id` (Long) | `id BIGINT UNSIGNED` | ✅ |
| `doctor` (Doctor FK) | `doctor_id BIGINT UNSIGNED` | ✅ |
| `dayOfWeek` (byte) | `day_of_week TINYINT UNSIGNED` | ✅ |
| `startTime` (LocalTime) | `start_time TIME` | ✅ |
| `endTime` (LocalTime) | `end_time TIME` | ✅ |
| `recurring` (boolean) | `is_recurring BOOLEAN` | ✅ |
| `effectiveFrom` (LocalDate) | `effective_from DATE` | ✅ |
| `effectiveUntil` (LocalDate) | `effective_until DATE` | ✅ |
| `createdAt` (LocalDateTime) | `created_at DATETIME(3)` | ✅ |

### Validation of Unique Constraint

Entity:

```java
@UniqueConstraint(
    name = "uk_availability_doctor_day_time",
    columnNames = {"doctor_id", "day_of_week", "start_time", "end_time"}
)
```

Database:

```sql
UNIQUE KEY uk_availability_doctor_day_time (doctor_id, day_of_week, start_time, end_time)
```

✅ Same constraint name and columns.

### Validation of Foreign Key

Entity:

```java
@JoinColumn(name = "doctor_id", nullable = false)
```

Database:

```sql
FOREIGN KEY (doctor_id) REFERENCES doctors(id) ON DELETE CASCADE
```

✅ Column name matches, nullability enforced.

### Validation of CHECK Constraints

The schema has:
```sql
CHECK (day_of_week BETWEEN 0 AND 6)
CHECK (start_time < end_time)
```

Hibernate does NOT validate CHECK constraints - it only validates schema structure. The CHECK constraints are enforced by MySQL at runtime.

## Learning Summary

**What was implemented:**

1. **@Entity + @Table** — Maps Java class to `doctor_availability` table
2. **@ManyToOne** — Many availability slots per doctor relationship
3. **@JoinColumn** — Foreign key column mapping
4. **FetchType.LAZY** — Avoids unnecessary JOINs when listing availability
5. **Time types** — `LocalTime` for TIME columns, `LocalDate` for DATE columns
6. **Byte mapping** — TINYINT UNSIGNED → byte for day of week
7. **Derived queries** — `findByDoctorId`, `findByDoctorIdAndDayOfWeek`
8. **Unique constraint** — Composite unique key on doctor+day+time
9. **Transactional tests** — Rollback prevents permanent test data

**Key concepts:**

- Configuration tables (availability) vs transactional tables (appointments)
- Many-to-one: many slots reference one doctor
- LAZY loading for performance when doctor data not needed
- Time types map naturally: TIME → LocalTime, DATE → LocalDate
- Composite unique constraints prevent duplicate availability slots