# JPA Appointment + AppointmentStatusHistory Domain

A beginner-friendly walkthrough of the JPA entities introduced in **Step 11**, covering appointment booking and its audit trail.

---

## The Big Picture

```
Appointment entity
    ↓   maps to `appointments` table
    ↓   references Doctor, Patient
    ↓   has OneToMany → AppointmentStatusHistory
AppointmentStatusHistory entity
    ↓   maps to `appointment_status_history` table
    ↓   ManyToOne back to Appointment
    ↓   optional ManyToOne to User (who changed status)
```

Appointments store scheduled clinic visits. AppointmentStatusHistory tracks every status change as an immutable audit trail.

---

## Why Appointment is an Entity

Appointment maps to the `appointments` table which stores scheduled clinic visits:

1. **Core business domain** — appointments are the central transaction in a clinic booking system
2. **Complex relationships** — connects patients, doctors, time slots, and fees
3. **Has its own lifecycle** — scheduled → confirmed → completed or cancelled
4. **Audit requirements** — status changes must be tracked with timestamps and actors

The `appointments` table stores:
- Who: patient_id, doctor_id
- When: appointment_date, start_time, end_time
- What: reason, notes, status
- How much: consultation_fee_snapshot (fee at booking time, immutable)
- Audit: created_at, updated_at, confirmed_at, completed_at

## Why AppointmentStatus is an Enum

```java
public enum AppointmentStatus {
    SCHEDULED,
    CONFIRMED,
    COMPLETED,
    CANCELLED,
    NO_SHOW
}
```

This is a **closed set of states** defined by business rules, not user data:

- Only these five states exist
- Database enforces it: `CHECK (status IN ('SCHEDULED','CONFIRMED','COMPLETED','CANCELLED','NO_SHOW'))`
- Java enforces it: `@Enumerated(EnumType.STRING)` stores the name, validated at compile-time
- Adding a new status requires a schema migration

**Why STRING not ORDINAL?** `EnumType.STRING` stores `"CONFIRMED"` in the database. `ORDINAL` would store `1` (the enum's position), which breaks if you reorder values. STRING is readable in SQL and safe.

## Appointment → Doctor Relationship (Many-to-One)

Schema:
```sql
appointments.doctor_id BIGINT UNSIGNED NOT NULL
FOREIGN KEY (doctor_id) REFERENCES doctors(id) ON DELETE RESTRICT
```

Mapping:
```java
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "doctor_id", nullable = false)
private Doctor doctor;
```

- **Many appointments belong to one doctor** — a doctor has a schedule of appointments
- **LAZY** — doctor is loaded only when `getDoctor()` is called
- **optional = false** — every appointment must have a doctor
- **ON DELETE RESTRICT** — cannot delete a doctor who has appointments (business rule: preserve appointment history)

## Appointment → Patient Relationship (Many-to-One)

Schema:
```sql
appointments.patient_id BIGINT UNSIGNED NOT NULL
FOREIGN KEY (patient_id) REFERENCES patients(id) ON DELETE RESTRICT
```

Mapping:
```java
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "patient_id", nullable = false)
private Patient patient;
```

Same pattern as doctor relationship — many appointments per patient, cannot delete a patient with appointments.

## Why consultation_fee_snapshot Uses BigDecimal

Schema:
```sql
consultation_fee_snapshot DECIMAL(10,2) NOT NULL
CHECK (consultation_fee_snapshot >= 0)
```

Mapping:
```java
@Column(name = "consultation_fee_snapshot", nullable = false, precision = 10, scale = 2)
private BigDecimal consultationFeeSnapshot;
```

**Why BigDecimal not double?**
- `double` has floating-point rounding errors: `0.1 + 0.2 != 0.3`
- Financial calculations must be exact
- `DECIMAL(10,2)` stores exactly 2 decimal places
- `BigDecimal` maps to it precisely

**Why snapshot?** The fee is copied from `doctors.consultation_fee` at booking time and never changes, even if the doctor raises their fee later. This preserves the agreed price for billing.

## Database-Generated Timestamps

Schema:
```sql
created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
confirmed_at DATETIME(3),
completed_at DATETIME(3)
```

Mapping:
```java
@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
private LocalDateTime createdAt;

@Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
private LocalDateTime updatedAt;

@Column(name = "confirmed_at")
private LocalDateTime confirmedAt;

@Column(name = "completed_at")
private LocalDateTime completedAt;
```

- **insertable/updatable = false** on created_at/updated_at → MySQL generates them, Hibernate reads them back
- **confirmed_at/completed_at** are nullable and set by application code when status changes
- Test pattern for DB-generated timestamps: `save()` → `entityManager.flush()` → `entityManager.refresh()` → assert not null

## Why AppointmentStatusHistory is an Entity

AppointmentStatusHistory maps to the `appointment_status_history` table, an **immutable audit trail**:

1. **Regulatory requirement** — healthcare systems must track who changed what and when
2. **Never updated or deleted** — records are permanent
3. **Optional actor** — changed_by_user_id can be NULL if the user is later deleted (ON DELETE SET NULL)
4. **Ordered by time** — changed_at timestamps let you replay appointment lifecycle

The V1 design does NOT enforce business rules like "CANCELLED must come before COMPLETED". That validation belongs in service-layer logic, not yet implemented.

## Appointment → AppointmentStatusHistory (One-to-Many)

Schema:
```sql
appointment_status_history.appointment_id BIGINT UNSIGNED NOT NULL
FOREIGN KEY (appointment_id) REFERENCES appointments(id) ON DELETE RESTRICT
```

Mapping on Appointment:
```java
@OneToMany(mappedBy = "appointment", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
private List<AppointmentStatusHistory> statusHistory = new ArrayList<>();
```

Mapping on AppointmentStatusHistory:
```java
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "appointment_id", nullable = false)
private Appointment appointment;
```

- **One appointment has many status-history records** — the audit trail
- **mappedBy = "appointment"** — AppointmentStatusHistory owns the foreign key
- **LAZY** — history loaded only when `getStatusHistory()` is called
- **ON DELETE RESTRICT** — cannot delete an appointment with history (preserve audit trail)
- **CascadeType.ALL** — if you delete an appointment entity in Java, Hibernate cascades to status history (but DB constraint prevents this unless history is empty)

## AppointmentStatusHistory → User (changed_by_user_id)

Schema:
```sql
changed_by_user_id BIGINT UNSIGNED
FOREIGN KEY (changed_by_user_id) REFERENCES users(id) ON DELETE SET NULL
```

Mapping:
```java
@ManyToOne(fetch = FetchType.LAZY, optional = true)
@JoinColumn(name = "changed_by_user_id")
private User changedByUser;
```

- **optional = true** — changedByUser can be NULL
- **ON DELETE SET NULL** — if the user is deleted, the history record stays but loses its actor reference
- **Why nullable?** Preserves audit trail even when users leave the organization

This is the **only relationship in V1 schema where ON DELETE SET NULL is used** — everywhere else uses RESTRICT (prevent deletion) or CASCADE (propagate deletion).

## Repository Derived Queries

```java
// AppointmentRepository
List<Appointment> findByDoctorId(Long doctorId);
List<Appointment> findByPatientId(Long patientId);
List<Appointment> findByStatus(AppointmentStatus status);
List<Appointment> findByDoctorIdAndAppointmentDate(Long doctorId, LocalDate appointmentDate);
List<Appointment> findByPatientIdAndAppointmentDate(Long patientId, LocalDate appointmentDate);
```

Spring Data JPA parses method names:
- `findBy` → SELECT
- `DoctorId` → WHERE doctor_id = ?
- `And` → AND
- `AppointmentDate` → appointment_date = ?

```java
// AppointmentStatusHistoryRepository
List<AppointmentStatusHistory> findByAppointmentId(Long appointmentId);
List<AppointmentStatusHistory> findByAppointmentIdOrderByChangedAtAsc(Long appointmentId);
```

- `OrderByChangedAtAsc` → ORDER BY changed_at ASC (oldest first, chronological audit trail)

## Transactional Repository Tests

All tests are annotated:
```java
@SpringBootTest
@Transactional
class AppointmentRepositoryTest { ... }
```

- **@SpringBootTest** — loads full Spring context with real MySQL connection
- **@Transactional** — each test runs in a transaction that **rolls back after the test**
- **Why rollback?** Tests leave no data behind in the shared dev database

Test isolation pattern:
```java
@Test
void testName() {
    Doctor doctor = createTestDoctor("doctor1@test.com", "Cardiology");  // requires Specialty
    Patient patient = createTestPatient("patient1@test.com");
    Appointment appt = appointmentRepository.save(new Appointment(...));
    // assertions
}  // transaction rolls back — doctor, patient, appointment deleted
```

Each test creates its own data and cleans up automatically.

## What is NOT Implemented Yet

Step 11 covers **entities, mappings, and repository tests only**. The following are deliberately missing:

- **No business logic** — no validation that status transitions are valid (e.g., cannot go from COMPLETED to SCHEDULED)
- **No conflict checking** — no verification that doctor is available at the requested time
- **No controllers or services** — no REST API, no transaction boundaries beyond tests
- **No authentication** — changedByUser is populated manually in tests
- **No cancellation workflow** — cancellation_reason is a nullable column but no enforcement that it's required when status = CANCELLED

These belong in the service layer, which Step 11 does not implement.

---

## How it Fits Together in MedBook

```
MySQL (Flyway V1: appointments, appointment_status_history tables)
   ↑
   | validates at startup (ddl-auto=validate)
   |
Hibernate 7.4.5.Final
   ↑
   | @Entity / @ManyToOne / @OneToMany / @Enumerated
   |
com.medbook.entity.Appointment, AppointmentStatusHistory, AppointmentStatus
   ↑
   | Spring Data JPA proxies
   |
AppointmentRepository, AppointmentStatusHistoryRepository
   ↑
   | (no services or controllers yet — next step)
JUnit integration tests (@SpringBootTest, @Transactional, real MySQL)
```

## What Was Verified

- **83 tests, 0 failures, 0 errors**
- Hibernate validated the Flyway schema (no mismatch between entities and tables)
- `AppointmentRepository.findByDoctorId(...)` works against live MySQL
- `AppointmentStatusHistoryRepository.findByAppointmentIdOrderByChangedAtAsc(...)` returns chronological audit trail
- Database-generated timestamps populate correctly after flush/refresh
- Lazy relationships (doctor, patient, status history) load on demand
- Transaction rollback keeps the dev database clean (0 appointments after tests)
- `GET /api/v1/hello` → `{"message":"Welcome to MedBook API"}`
- `GET /actuator/health` → `UP`
- Exactly 10 tables in medbook database
- Flyway version 1, success = 1

## Beginner's Takeaway

Step 11 teaches:

1. **Entities map to existing tables** — Flyway creates the schema, JPA validates it
2. **Relationships have direction and ownership** — `@JoinColumn` marks the owner
3. **Enums map business rules** — closed sets of states, validated at compile-time and runtime
4. **BigDecimal for money** — exact decimal arithmetic, no floating-point errors
5. **Audit trails are immutable** — AppointmentStatusHistory records are never updated or deleted
6. **ON DELETE behavior matters** — RESTRICT preserves history, SET NULL preserves audit when actors are deleted
7. **Derived queries are free** — Spring Data JPA writes SQL from method names
8. **Test isolation via transactions** — @Transactional + rollback keeps tests independent

The next step will add service-layer business logic and REST controllers on top of this foundation.
