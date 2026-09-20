# MedBook Database Design

## Current Database State

**MySQL Database:** `medbook`
**Application Tables:** 0
**Status:** Database exists but contains no application tables.
**Note:** This document is a design only. No tables, migrations, or JPA entities have been created yet. The MySQL database has NOT been modified.

## Approved V1 Design Decisions

These decisions were approved during the Step 5 architecture review and are final for V1:

1. **Nine-table model retained** — `users`, `roles`, `user_roles`, `doctors`, `patients`, `specialties`, `doctor_availability`, `appointments`, `appointment_status_history`.
2. **Multi-role users** — M:N via `user_roles` junction table. A user may hold multiple roles (e.g., DOCTOR and PATIENT simultaneously).
3. **One primary specialty per doctor** — `doctors.specialty_id` is NOT NULL. Multiple specialties per doctor is a future enhancement.
4. **`patients.is_active` added** — BOOLEAN NOT NULL DEFAULT TRUE, consistent with the soft-deletion strategy.
5. **No nondeterministic CHECK constraints** — all CHECK constraints using `CURDATE()`, `CURRENT_DATE`, `NOW()`, or similar current-time functions were removed. "Date-of-birth cannot be in the future" and "appointment cannot be in the past" validations belong in the **application layer**.
6. **Appointment status uses VARCHAR + CHECK**, not MySQL ENUM. Allowed values: `SCHEDULED`, `CONFIRMED`, `COMPLETED`, `CANCELLED`, `NO_SHOW`. The Java application keeps a type-safe enum for backend code.
7. **Roles remain a normalized table** — no MySQL ENUM for role names. Seed values (`ADMIN`, `DOCTOR`, `PATIENT`) are enforced at the application/seed-data level, not by a database enum.
8. **`appointments.consultation_fee_snapshot` added** — DECIMAL(10,2) NOT NULL, preserving the fee that applied at booking time even if the doctor's current fee later changes.
9. **Timezone finalized for V1** — clinic timezone is `Asia/Kolkata`. `appointment_date`/`start_time`/`end_time` represent clinic-local appointment time. System/audit timestamps (`created_at`, `updated_at`, `last_login_at`, `assigned_at`, `changed_at`) are stored as UTC values, represented consistently by the application. Multi-clinic/timezone support is a future enhancement.
10. **Concurrency control finalized** — appointment creation locks the doctor row with `SELECT ... FOR UPDATE` before performing any conflict checks (see "Appointment Concurrency Strategy").
11. **No partial unique indexes** — MySQL does not support partial/filtered unique indexes. `doctor_availability` uses plain indexes for lookup; overlap validation of availability windows happens in the service layer.
12. **Redundant indexes/constraints removed** — no index is created merely because a column exists. `uk_user_role`, `idx_users_email`, `idx_availability_doctor_day`, and `idx_availability_times` were dropped (details in Index Strategy).
13. **Insurance conditional rule** — if `has_insurance = TRUE`, `insurance_provider` and `insurance_policy_number` must be present. Enforced by the application (primary) with a deterministic CHECK constraint as a data-quality backstop.
14. **Status timestamps** — `confirmed_at` / `completed_at` are kept; their consistency with `status` is enforced primarily by the service layer (e.g., CONFIRMED → confirmed_at set, COMPLETED → completed_at set). No complex workflow CHECK constraints in the database.
15. **Soft deletion finalized** — `is_active` flags on users, doctors, patients, specialties. Appointments are never physically deleted in normal use; cancellation is `status = 'CANCELLED'`. History is preserved.

## Design Goals

1. **Relational Integrity** - Enforce relationships between patients, doctors, and appointments
2. **Performance** - Optimize for common queries: appointment lookup, doctor search
3. **Auditability** - Track appointment status changes and user actions
4. **Security** - Separate authentication data from profile information
5. **Flexibility** - Support multiple roles, availability windows, and appointment types
6. **Future-Proofing** - Design for potential features (see "Deferred / Future Enhancements")

## Proposed Entities

### Core Entities
1. `users` - Authentication and account data
2. `roles` - Role definitions (ADMIN, DOCTOR, PATIENT)
3. `user_roles` - User-to-role assignments
4. `doctors` - Doctor profile and professional data
5. `patients` - Patient profile and medical data
6. `specialties` - Medical specialties
7. `doctor_availability` - Weekly availability windows
8. `appointments` - Appointment records
9. `appointment_status_history` - Audit trail of status changes

## Table-by-Table Design

### Table 1: `users`
**Purpose:** Authentication and account management

| Column | SQL Type | Nullable | PK | Default | Description |
|--------|----------|----------|----|---------|-------------|
| `id` | BIGINT UNSIGNED | NO | ✅ | AUTO_INCREMENT | Primary key |
| `email` | VARCHAR(255) | NO | | | Unique email address |
| `password_hash` | VARCHAR(255) | NO | | | Hashed password (one-way hash, never reversibly encrypted) |
| `first_name` | VARCHAR(100) | NO | | | User's first name |
| `last_name` | VARCHAR(100) | NO | | | User's last name |
| `phone_number` | VARCHAR(20) | NO | | | Contact phone |
| `is_active` | BOOLEAN | NO | | TRUE | Account status (soft-deletion flag) |
| `is_locked` | BOOLEAN | NO | | FALSE | Login lock flag |
| `failed_login_attempts` | TINYINT UNSIGNED | NO | | 0 | Failed login count |
| `last_login_at` | DATETIME(3) | YES | | | Last successful login (UTC) |
| `created_at` | DATETIME(3) | NO | | CURRENT_TIMESTAMP(3) | Record creation (UTC) |
| `updated_at` | DATETIME(3) | NO | | CURRENT_TIMESTAMP(3) | Record update (UTC) |

**Constraints:**
- `UNIQUE uk_users_email (email)` — this unique index also serves as the login lookup index; no separate `idx_users_email` is created
- `CHECK phone_number REGEXP '^\+?[0-9\s\-]+$'` (MySQL 8.0+)

**Notes:** Stores core authentication data. Doctor/patient-specific data links via foreign keys.

### Table 2: `roles`
**Purpose:** Role definitions for RBAC (Role-Based Access Control)

| Column | SQL Type | Nullable | PK | Default | Description |
|--------|----------|----------|----|---------|-------------|
| `id` | BIGINT UNSIGNED | NO | ✅ | AUTO_INCREMENT | Primary key |
| `name` | VARCHAR(50) | NO | | | Role name |
| `description` | VARCHAR(255) | YES | | | Role description |

**Constraints:**
- `UNIQUE uk_roles_name (name)`

**Role value enforcement:** There is deliberately **no database ENUM and no CHECK constraint** on `name`. The allowed role values (`ADMIN`, `DOCTOR`, `PATIENT`) are enforced at the **application/seed-data level**: the seed migration inserts exactly these three rows, and application code maps authorities to these names. This keeps the table normalized and extensible without schema changes.

**Notes:** Small static table. Normalized for clarity and future role expansion. No timestamps by design (immutable seed-managed reference data).

### Table 3: `user_roles`
**Purpose:** Many-to-many relationship between users and roles

| Column | SQL Type | Nullable | PK | Default | Description |
|--------|----------|----------|----|---------|-------------|
| `user_id` | BIGINT UNSIGNED | NO | ✅ | | References users.id |
| `role_id` | BIGINT UNSIGNED | NO | ✅ | | References roles.id |
| `assigned_at` | DATETIME(3) | NO | | CURRENT_TIMESTAMP(3) | Assignment timestamp (UTC) |

**Constraints:**
- `PRIMARY KEY pk_user_roles (user_id, role_id)` — the composite PK alone guarantees uniqueness of (user, role); **no separate `uk_user_role` unique constraint** (it would duplicate the PK)
- `FOREIGN KEY fk_user_roles_user_id (user_id) REFERENCES users(id) ON DELETE CASCADE`
- `FOREIGN KEY fk_user_roles_role_id (role_id) REFERENCES roles(id) ON DELETE RESTRICT`

**Notes:** Users can have multiple roles (e.g., a doctor who is also a patient). MySQL automatically indexes FK columns; the PK index covers (user_id, role_id) lookups in both common directions.

### Table 4: `specialties`
**Purpose:** Medical specialties for doctor categorization

| Column | SQL Type | Nullable | PK | Default | Description |
|--------|----------|----------|----|---------|-------------|
| `id` | BIGINT UNSIGNED | NO | ✅ | AUTO_INCREMENT | Primary key |
| `name` | VARCHAR(100) | NO | | | Specialty name |
| `description` | TEXT | YES | | | Detailed description |
| `is_active` | BOOLEAN | NO | | TRUE | Active status (soft-deletion flag) |
| `created_at` | DATETIME(3) | NO | | CURRENT_TIMESTAMP(3) | Creation timestamp (UTC) |

**Constraints:**
- `UNIQUE uk_specialties_name (name)`

**Indexes:** none beyond the unique index. No index on `is_active` — the table is small and low-selectivity single-flag indexes add write cost without query benefit. If a concrete "list active specialties" query under heavy load ever needs one, add it then.

**Notes:** Normalized for consistency. Example values: Cardiology, Pediatrics, General Practice.

### Table 5: `doctors`
**Purpose:** Doctor professional and profile information

| Column | SQL Type | Nullable | PK | Default | Description |
|--------|----------|----------|----|---------|-------------|
| `id` | BIGINT UNSIGNED | NO | ✅ | AUTO_INCREMENT | Primary key |
| `user_id` | BIGINT UNSIGNED | NO | | | References users.id |
| `specialty_id` | BIGINT UNSIGNED | NO | | | References specialties.id (exactly one primary specialty in V1) |
| `medical_license_number` | VARCHAR(50) | NO | | | Professional license |
| `years_experience` | TINYINT UNSIGNED | NO | | | Years of practice |
| `bio` | TEXT | YES | | | Professional biography |
| `consultation_fee` | DECIMAL(10,2) | NO | | | Current appointment fee |
| `is_active` | BOOLEAN | NO | | TRUE | Active status (soft-deletion flag) |
| `profile_picture_url` | VARCHAR(500) | YES | | | Profile image URL |
| `created_at` | DATETIME(3) | NO | | CURRENT_TIMESTAMP(3) | Creation timestamp (UTC) |
| `updated_at` | DATETIME(3) | NO | | CURRENT_TIMESTAMP(3) | Update timestamp (UTC) |

**Constraints:**
- `FOREIGN KEY fk_doctors_user_id (user_id) REFERENCES users(id) ON DELETE RESTRICT`
- `FOREIGN KEY fk_doctors_specialty_id (specialty_id) REFERENCES specialties(id) ON DELETE RESTRICT`
- `UNIQUE uk_doctors_user_id (user_id)` — enforces one-to-one with users
- `UNIQUE uk_doctors_license (medical_license_number)`
- `CHECK consultation_fee >= 0`
- `CHECK years_experience BETWEEN 0 AND 80`

**Indexes:** The unique `uk_doctors_user_id` doubles as the FK index on `user_id`. MySQL's InnoDB FK support requires an index on `specialty_id`; the explicit `idx_doctors_specialty (specialty_id)` names that required FK index (it is not optional, but it is not a duplicate of any other index either).

**Notes:** One-to-one relationship with users. `consultation_fee` is the doctor's **current** fee; historical appointments keep their own `consultation_fee_snapshot` (see appointments). Active flag for deactivation without deletion.

### Table 6: `patients`
**Purpose:** Patient profile and medical information

| Column | SQL Type | Nullable | PK | Default | Description |
|--------|----------|----------|----|---------|-------------|
| `id` | BIGINT UNSIGNED | NO | ✅ | AUTO_INCREMENT | Primary key |
| `user_id` | BIGINT UNSIGNED | NO | | | References users.id |
| `date_of_birth` | DATE | NO | | | Birth date (clinic-local calendar date) |
| `address_line1` | VARCHAR(255) | YES | | | Primary address |
| `address_line2` | VARCHAR(255) | YES | | | Secondary address |
| `city` | VARCHAR(100) | YES | | | City |
| `state_province` | VARCHAR(50) | YES | | | State/Province |
| `postal_code` | VARCHAR(20) | YES | | | Postal code |
| `country` | VARCHAR(50) | YES | | | Country |
| `emergency_contact_name` | VARCHAR(200) | YES | | | Emergency contact |
| `emergency_contact_phone` | VARCHAR(20) | YES | | | Emergency phone |
| `has_insurance` | BOOLEAN | NO | | FALSE | Insurance status |
| `insurance_provider` | VARCHAR(100) | YES | | | Insurance provider (required when has_insurance = TRUE) |
| `insurance_policy_number` | VARCHAR(50) | YES | | | Policy number (required when has_insurance = TRUE) |
| `allergies` | TEXT | YES | | | Known allergies |
| `is_active` | BOOLEAN | NO | | TRUE | Active status (soft-deletion flag) |
| `created_at` | DATETIME(3) | NO | | CURRENT_TIMESTAMP(3) | Creation timestamp (UTC) |
| `updated_at` | DATETIME(3) | NO | | CURRENT_TIMESTAMP(3) | Update timestamp (UTC) |

**Constraints:**
- `FOREIGN KEY fk_patients_user_id (user_id) REFERENCES users(id) ON DELETE RESTRICT`
- `UNIQUE uk_patients_user_id (user_id)`
- `CHECK (has_insurance = FALSE OR (insurance_provider IS NOT NULL AND insurance_policy_number IS NOT NULL))` — deterministic CHECK backstop for the insurance rule
- **Date-of-birth validation is application-layer only:** "cannot be in the future" (and a sensible lower bound, e.g. '1900-01-01') is enforced by the service. No `CURDATE()`/`CURRENT_DATE` CHECK constraint is used, because MySQL rejects nondeterministic functions in CHECK constraints.

**Notes:** One-to-one relationship with users. Stores personal and contact information. `is_active` added to match the documented soft-deletion strategy.

### Table 7: `doctor_availability`
**Purpose:** Weekly availability windows for doctors

| Column | SQL Type | Nullable | PK | Default | Description |
|--------|----------|----------|----|---------|-------------|
| `id` | BIGINT UNSIGNED | NO | ✅ | AUTO_INCREMENT | Primary key |
| `doctor_id` | BIGINT UNSIGNED | NO | | | References doctors.id |
| `day_of_week` | TINYINT UNSIGNED | NO | | | 0-6 (Sunday=0) |
| `start_time` | TIME | NO | | | Window start (clinic-local, Asia/Kolkata) |
| `end_time` | TIME | NO | | | Window end (clinic-local, Asia/Kolkata) |
| `is_recurring` | BOOLEAN | NO | | TRUE | Recurring availability |
| `effective_from` | DATE | YES | | | Start date for recurring window |
| `effective_until` | DATE | YES | | | End date for recurring window |
| `created_at` | DATETIME(3) | NO | | CURRENT_TIMESTAMP(3) | Creation timestamp (UTC) |

**Constraints:**
- `FOREIGN KEY fk_availability_doctor_id (doctor_id) REFERENCES doctors(id) ON DELETE CASCADE`
- `CHECK day_of_week BETWEEN 0 AND 6`
- `CHECK start_time < end_time`
- `CHECK effective_from <= effective_until` — passes when either side is NULL (MySQL NULL semantics), which is the intended "unbounded" meaning

**Uniqueness / overlap policy:**
- **MySQL does not support partial or filtered unique indexes.** The previously proposed "partial unique via effective_from" was incorrect and has been removed.
- A doctor **may** have multiple non-overlapping windows on the same weekday (e.g., 09:00–12:00 and 14:00–17:00).
- **Overlapping windows on the same weekday are rejected by application validation** in the service layer, not by a database constraint.

**Indexes:**
- `uk_availability_doctor_day_time (doctor_id, day_of_week, start_time, end_time)` — plain UNIQUE index preventing exact duplicate windows; its leading columns (doctor_id, day_of_week) also serve as the efficient lookup index for "availability for doctor X on weekday Y". No separate `idx_availability_doctor_day` is created — it would be a redundant prefix of this index.
- **`idx_availability_times (start_time, end_time)` has been removed** — no concrete query needs a cross-doctor raw time-of-day index. Re-add only if a real query emerges.

**Notes:** Supports recurring weekly availability. Times are clinic-local (Asia/Kolkata). The unique index guarantees exact duplicates cannot exist; semantic overlap checking lives in the service layer.

### Table 8: `appointments`
**Purpose:** Appointment booking and scheduling

| Column | SQL Type | Nullable | PK | Default | Description |
|--------|----------|----------|----|---------|-------------|
| `id` | BIGINT UNSIGNED | NO | ✅ | AUTO_INCREMENT | Primary key |
| `patient_id` | BIGINT UNSIGNED | NO | | | References patients.id |
| `doctor_id` | BIGINT UNSIGNED | NO | | | References doctors.id |
| `appointment_date` | DATE | NO | | | Appointment date (clinic-local, Asia/Kolkata) |
| `start_time` | TIME | NO | | | Start time (clinic-local) |
| `end_time` | TIME | NO | | | End time (clinic-local) |
| `reason` | VARCHAR(500) | YES | | | Appointment reason |
| `notes` | TEXT | YES | | | Doctor notes |
| `status` | VARCHAR(20) | NO | | 'SCHEDULED' | Current status (see below) |
| `cancellation_reason` | VARCHAR(255) | YES | | | If cancelled |
| `consultation_fee_snapshot` | DECIMAL(10,2) | NO | | | Fee at booking time (see Fee Strategy) |
| `created_at` | DATETIME(3) | NO | | CURRENT_TIMESTAMP(3) | Creation timestamp (UTC) |
| `updated_at` | DATETIME(3) | NO | | CURRENT_TIMESTAMP(3) | Update timestamp (UTC) |
| `confirmed_at` | DATETIME(3) | YES | | | Confirmation timestamp (UTC) |
| `completed_at` | DATETIME(3) | YES | | | Completion timestamp (UTC) |

**Status representation:** `VARCHAR(20)` with a database CHECK constraint (not MySQL ENUM):

```sql
CHECK status IN ('SCHEDULED','CONFIRMED','COMPLETED','CANCELLED','NO_SHOW')
```

The Java application keeps a type-safe enum (`AppointmentStatus`) for backend code. VARCHAR + CHECK is preferred over ENUM because adding a value is a plain data/DDL change with no ENUM-reordering pitfalls, and the column reads as plain text in queries and tooling.

**Constraints:**
- `FOREIGN KEY fk_appointments_patient_id (patient_id) REFERENCES patients(id) ON DELETE RESTRICT`
- `FOREIGN KEY fk_appointments_doctor_id (doctor_id) REFERENCES doctors(id) ON DELETE RESTRICT`
- `CHECK start_time < end_time`
- `CHECK status IN ('SCHEDULED','CONFIRMED','COMPLETED','CANCELLED','NO_SHOW')`
- `CHECK (status != 'CANCELLED' OR cancellation_reason IS NOT NULL)`
- `CHECK consultation_fee_snapshot >= 0`
- **Appointment "cannot be in the past" validation is application-layer only.** No `CURDATE()`/`CURRENT_DATE` CHECK constraint is used (nondeterministic functions are rejected by MySQL in CHECK constraints).
- **Status↔timestamp consistency** (CONFIRMED → `confirmed_at` set, COMPLETED → `completed_at` set, CANCELLED → `cancellation_reason` set) is enforced primarily by the **service layer**. No complex workflow CHECK constraints are added to the database.
- **Overlap prevention: application layer** (see "Appointment Concurrency Strategy").

**Indexes:**
- `idx_appointments_doctor_date (doctor_id, appointment_date)` — doctor's daily schedule; also the access path for the conflict-check query
- `idx_appointments_patient_date (patient_id, appointment_date)` — patient history / "my appointments"
- `idx_appointments_status (status)` — kept only for the explicit dashboards that filter by status (e.g., "all pending confirmations"); it is acknowledged as low-selectivity and is the first candidate for removal if no such dashboard materializes

**Removed indexes:** the standalone `idx_appointments_date (appointment_date)` was dropped — the two composite indexes above cover the realistic access patterns, and a single-column date index across all doctors/patients has no concrete query behind it.

**Notes:** Core appointment data. Status transitions are audited in status_history. `consultation_fee_snapshot` records the fee agreed at booking time.

### Table 9: `appointment_status_history`
**Purpose:** Audit trail for appointment status changes

| Column | SQL Type | Nullable | PK | Default | Description |
|--------|----------|----------|----|---------|-------------|
| `id` | BIGINT UNSIGNED | NO | ✅ | AUTO_INCREMENT | Primary key |
| `appointment_id` | BIGINT UNSIGNED | NO | | | References appointments.id |
| `status` | VARCHAR(20) | NO | | | Status value at the time of change (same CHECK value list as appointments.status) |
| `changed_by_user_id` | BIGINT UNSIGNED | YES | | | References users.id |
| `notes` | VARCHAR(500) | YES | | | Change notes |
| `changed_at` | DATETIME(3) | NO | | CURRENT_TIMESTAMP(3) | Change timestamp (UTC) |

**Status representation:** `VARCHAR(20)` with the same `CHECK status IN (...)` constraint as appointments (not ENUM), for consistency.

**Constraints:**
- `FOREIGN KEY fk_status_history_appointment_id (appointment_id) REFERENCES appointments(id) ON DELETE RESTRICT`
- `FOREIGN KEY fk_status_history_changed_by (changed_by_user_id) REFERENCES users(id) ON DELETE SET NULL`
- `CHECK status IN ('SCHEDULED','CONFIRMED','COMPLETED','CANCELLED','NO_SHOW')`
- No UNIQUE on `(appointment_id, status)` — repeat transitions (e.g., SCHEDULED → CONFIRMED → CANCELLED) are legitimate.

**Audit trail protection:** `ON DELETE RESTRICT` prevents accidental physical deletion of appointments that have audit history. MedBook application logic never physically deletes appointments during normal use — cancellation is represented by `status = 'CANCELLED'`, and historical appointments are preserved indefinitely. The RESTRICT constraint provides an additional database safety mechanism: if an appointment is accidentally targeted for physical deletion (e.g., during manual database maintenance), the foreign key constraint will reject the deletion rather than silently cascade-deleting the audit trail.

**Indexes:**
- `idx_status_history_appointment (appointment_id)` — names the FK index; also serves "history for appointment X"
- `idx_status_history_created (changed_at)` — time-range audit queries

**Notes:** Tracks who changed what and when for audit purposes. `changed_by_user_id` is nullable (system-generated transitions). The `changed_at` column name (not `created_at`) clarifies that this timestamp records when the status change occurred.

## Relationship Explanation

### User-Role Hierarchy
- **User-Role**: Many-to-Many via `user_roles` junction table
- **User-Doctor**: One-to-One, **optional on the user side** (a user need not be a doctor), mandatory on the doctor side
- **User-Patient**: One-to-One, optional on the user side, mandatory on the patient side
- **User-StatusHistory**: Many-to-One from history to user (each history row references at most one nullable user) — **not many-to-many**

### Doctor Relationships
- **Doctor-Specialty**: Many-to-One (doctor belongs to exactly one primary specialty in V1)
- **Doctor-Availability**: One-to-Many (doctor has many availability windows)
- **Doctor-Appointment**: One-to-Many (doctor has many appointments)

### Patient Relationships
- **Patient-Appointment**: One-to-Many (patient has many appointments)

### Appointment Relationships
- **Appointment-StatusHistory**: One-to-Many (appointment has many status changes; the service creates an initial SCHEDULED row at booking)

## Primary Key Strategy

- All tables use `BIGINT UNSIGNED AUTO_INCREMENT` for primary keys
- Natural keys (email, license number) have unique constraints but not PK
- Junction table (`user_roles`) uses a composite PK — no duplicate unique constraint on top of it
- Consistent `id` naming across all tables
- `BIGINT` provides room for millions of records

## Foreign Key Strategy

- **ON DELETE RESTRICT** for core relationships (doctors, patients, appointments) - prevents accidental deletion
- **ON DELETE CASCADE** for dependent data (availability, status history) - cleans up orphaned records
- **ON DELETE SET NULL** for optional references (changed_by_user_id) - preserves history even if a user record were ever removed
- ON UPDATE behavior is intentionally left at the MySQL default (RESTRICT); surrogate PKs are never updated
- MySQL InnoDB automatically requires/creates indexes for foreign-key columns; explicit indexes are only named where they add a distinct access path — no redundant duplicates

## Constraints

### Business Logic Constraints
1. **Appointment Dates**: Cannot schedule appointments in the past (**application layer** — no CURDATE()/NOW() CHECK)
2. **Date of Birth**: Cannot be in the future (**application layer** — no CURDATE()/CURRENT_DATE() CHECK)
3. **Availability Times**: `start_time` must be before `end_time` (CHECK)
4. **Fee Validation**: `consultation_fee >= 0` and `consultation_fee_snapshot >= 0` (CHECK)
5. **Phone Format**: Basic phone number validation (CHECK REGEXP)
6. **Status Values**: `status IN ('SCHEDULED','CONFIRMED','COMPLETED','CANCELLED','NO_SHOW')` (CHECK, appointments + history)
7. **Cancellation Reason**: Required when status is `CANCELLED` (CHECK)
8. **Insurance Completeness**: `has_insurance = TRUE` requires provider and policy number (CHECK backstop + application validation)
9. **Availability Overlap**: Non-overlapping windows per doctor/weekday (**application layer**)
10. **Appointment Overlap**: No conflicting appointments per doctor (**application layer + doctor-row locking**)
11. **Status-Timestamp Consistency**: CONFIRMED/COMPLETED set their timestamps (**service layer, primary enforcement**)
12. **Role Values**: ADMIN / DOCTOR / PATIENT enforced at the **application/seed-data level** (no DB enum or CHECK)

### Data Integrity Constraints
1. **Unique Emails**: One email per user account
2. **Unique Licenses**: One medical license number per doctor
3. **Unique User-Doctor/Patient**: One doctor/patient record per user
4. **No Exact Duplicate Availability**: Same doctor/day/start/end blocked by unique index

## Index Strategy

### Indexes (final list)

**Principle: no index is created merely because a column exists.** MySQL InnoDB automatically requires indexes for foreign-key columns; where an existing UNIQUE or PK index already provides the needed access path, no duplicate is created.

| Table | Index | Columns | Reason |
|-------|-------|---------|--------|
| users | `uk_users_email` (UNIQUE) | (email) | Uniqueness + login lookup (replaces separate `idx_users_email`) |
| users | `idx_users_phone` | (phone_number) | Phone lookup |
| roles | `uk_roles_name` (UNIQUE) | (name) | Uniqueness + role-name lookup |
| user_roles | PK index | (user_id, role_id) | Covers both FK directions for this table |
| specialties | `uk_specialties_name` (UNIQUE) | (name) | Uniqueness + lookup |
| doctors | `uk_doctors_user_id` (UNIQUE) | (user_id) | 1:1 enforcement + FK lookup |
| doctors | `uk_doctors_license` (UNIQUE) | (medical_license_number) | License uniqueness + lookup |
| doctors | `idx_doctors_specialty` | (specialty_id) | Required FK index (InnoDB requirement, named explicitly) |
| patients | `uk_patients_user_id` (UNIQUE) | (user_id) | 1:1 enforcement + FK lookup |
| doctor_availability | `uk_availability_doctor_day_time` (UNIQUE) | (doctor_id, day_of_week, start_time, end_time) | Duplicate prevention + lookup by doctor/day (covers removed `idx_availability_doctor_day`) |
| appointments | `idx_appointments_doctor_date` | (doctor_id, appointment_date) | Doctor schedule + conflict check |
| appointments | `idx_appointments_patient_date` | (patient_id, appointment_date) | Patient history |
| appointments | `idx_appointments_status` | (status) | Status dashboards only (low selectivity; first candidate to drop) |
| appointment_status_history | `idx_status_history_appointment` | (appointment_id) | FK + "history for appointment" |
| appointment_status_history | `idx_status_history_created` | (created_at) | Time-range audits |

### Removed (redundant) Indexes and Constraints
- `uk_user_role (user_id, role_id)` — duplicate of the composite PK
- `idx_users_email` — duplicate of the unique email index
- `idx_availability_doctor_day` — redundant prefix of `uk_availability_doctor_day_time`
- `idx_availability_times` — no concrete query requires it
- `idx_specialties_active`, `idx_doctors_active` — low-selectivity single-flag indexes with no justified query
- `idx_appointments_date` — covered by composite indexes; no standalone-date query justified

### Covering Indexes
- Additional covering indexes to be added only when concrete query patterns are identified during development.

## Deletion Strategy

### Physical vs Soft Deletion
1. **Users**: Soft deletion via `is_active = FALSE`
   - Reason: Legal retention requirements, audit trails
   - Implementation: `is_active = FALSE` instead of DELETE

2. **Doctors / Patients**: Soft deletion via `is_active = FALSE` (both tables have the flag)
   - Reason: Historical data retention for appointments
   - Implementation: `is_active = FALSE` for deactivation

3. **Specialties**: Soft deletion via `is_active = FALSE`
   - Reason: Referenced by doctors; keeps history meaningful
   - Implementation: `is_active = FALSE`; physical DELETE blocked by RESTRICT FK while referenced

4. **Appointments**: Never physically deleted during normal application use
   - Cancellation is represented by `status = 'CANCELLED'` (with `cancellation_reason`)
   - Historical appointments and `appointment_status_history` are preserved for reporting and legal compliance

5. **Availability Slots**: Physical deletion allowed
   - Reason: Schedule updates are routine
   - Implementation: DELETE with cascade for dependent data (none currently)

6. **Roles**: Physical deletion restricted
   - Reason: Referential integrity; seed-managed reference data
   - Implementation: DELETE blocked if referenced (RESTRICT); do not delete seeded roles

7. **Appointment Status History**: Never deleted — append-only audit trail

### Audit Trail
1. **Appointment Status**: Full history in `appointment_status_history`
2. **User Actions**: Future extension with audit log table (deferred — see below)
3. **Timestamp Tracking**: `created_at`/`updated_at` on all mutable tables; roles is seed-managed immutable data with no timestamps

## Appointment Concurrency Strategy

### Conflict Condition

Two appointments conflict when they overlap in time for the same doctor on the same date, with **both** conditions true:

```sql
existing.start_time < requested.end_time
AND existing.end_time   > requested.start_time
```

Appointments with terminal/non-blocking statuses — as defined by the application rules (e.g., `CANCELLED`; the service layer owns this definition) — are **ignored** in the conflict check.

### Transaction Protocol (mandatory ordering)

When creating an appointment, the service transaction must execute these steps in this exact order:

```
BEGIN transaction
    ↓
1. Lock the doctor row using SELECT ... FOR UPDATE on doctors(id)
    ↓
2. Validate doctor availability (active, window covers requested slot, effective dates)
    ↓
3. Check overlapping active appointments
   (idx_appointments_doctor_date → filter by date + conflict condition + non-blocking statuses)
    ↓
4. Create the appointment row
   (with consultation_fee_snapshot copied from doctors.consultation_fee)
    ↓
5. Create the initial SCHEDULED status-history record
    ↓
COMMIT
```

**The doctor lock must always be acquired BEFORE performing appointment conflict checks.** This serializes all booking attempts for the same doctor on the lock itself, eliminating the race where two transactions both pass the conflict check and then both insert. Because all booking transactions lock the same doctor row first, deadlock risk between competing bookings is eliminated (consistent lock ordering: doctor row first, then appointment inserts).

### Rationale
- MySQL has no exclusion constraints (e.g., PostgreSQL's EXCLUDE), so time-range overlap cannot be enforced declaratively.
- A unique constraint alone cannot express partial overlap.
- The doctor-row lock converts the check-then-insert sequence into a serialized critical section per doctor.

## Timezone Handling (Finalized for V1)

### V1 Rule
- **Clinic timezone: `Asia/Kolkata`**
- `appointment_date`, `start_time`, `end_time` (appointments) and `start_time`, `end_time`, `effective_from`, `effective_until` (doctor_availability) represent **clinic-local appointment time**.
- **System/audit timestamps are stored as UTC values**, represented consistently by the application: `created_at`, `updated_at`, `last_login_at`, `assigned_at`, `changed_at`, `confirmed_at`, `completed_at`.
- The application is responsible for this representation: clinic-local values are stored/presented in Asia/Kolkata; audit timestamps are stored and exchanged in UTC and converted to clinic-local time only for display.
- **No mixed representations**: nothing else in the schema stores timezone-dependent values, so no per-row timezone metadata is needed in V1.

### Why this split
- Appointment/availability times must match what staff and patients see on the clinic calendar — clinic-local is the natural unit.
- Audit timestamps must be unambiguous and sortable across any future environments — UTC is the standard choice.

### Future Enhancement
- **Multi-clinic / timezone-per-clinic support is NOT part of V1.** When introduced, it would add a timezone column to a clinics table and reinterpret the clinic-local convention per clinic. Until then, exactly one clinic timezone (Asia/Kolkata) is assumed everywhere.

## Date/Time Strategy

### MySQL Data Types
1. **DATE** for appointment/availability/effective dates (`appointment_date`, `effective_from`, `effective_until`, `date_of_birth`)
2. **TIME** for time slots (`start_time`, `end_time`)
3. **DATETIME(3)** for all timestamps with millisecond precision, stored as UTC
4. **VARCHAR(20) + CHECK** for status values (not ENUM)

### Duration Calculation
- Appointment duration: `end_time - start_time`
- Store a separate `duration_minutes` column only if analytics later requires it
- Business rule: Standard 30/60 minute slots

## Money Strategy

### Representation Choice: `DECIMAL(10,2)`
1. **Precision**: `DECIMAL` avoids floating-point rounding errors
2. **Scale**: 2 decimal places for paise (INR subunit)
3. **Range**: `DECIMAL(10,2)` supports up to 99,999,999.99
4. **Why not FLOAT**: Avoids rounding errors in financial calculations
5. **Why not INT paise**: `DECIMAL` more readable, less conversion overhead

### Fee Strategy (Finalized)
1. **Current fee**: `doctors.consultation_fee` is the doctor's current fee, editable at any time
2. **Booking-time snapshot**: `appointments.consultation_fee_snapshot` (NOT NULL, CHECK >= 0) records the fee **associated with the appointment at booking time**. If the doctor later changes `consultation_fee`, existing appointments and historical billing remain accurate. The service copies the doctor's current fee into the snapshot inside the booking transaction (step 4 of the concurrency protocol).
3. **Currency**: Single currency (INR assumed under the Asia/Kolkata clinic). No conversion; no currency column in V1. Add a currency code column only when multi-currency becomes a real requirement.
4. **Tax**: Excluded from stored fees (added at billing time, if ever).

## Normalization Notes

### Third Normal Form (3NF) Compliance
1. **1NF**: All columns atomic, no repeating groups
2. **2NF**: All non-key columns depend on the entire primary key
3. **3NF**: No transitive dependencies

### Intentional Denormalization
1. **Appointment Status**: Current status stored in appointments table
   - Reason: Frequent query pattern, avoids join to history table
   - Maintained in lockstep with history by the service layer
2. **Consultation Fee Snapshot**: Denormalized copy of the doctor's fee at booking time
   - Reason: Preserves historical pricing accuracy after fee changes
3. **User Name**: Direct attribute of users (not derived) — no duplication

### Normalized Tables
1. **Specialties**: Normalized for consistency across doctors
2. **Roles**: Normalized for role management
3. **Availability**: Normalized for flexible scheduling

## Deferred / Future Enhancements

The following are **NOT part of V1**. They are documented so the V1 schema does not over-build, and so the migration path is understood:

1. **Multiple specialties per doctor** — would replace `doctors.specialty_id` with a `doctor_specialties` junction table. Deferred: V1 keeps exactly one primary specialty.
2. **Multiple clinics** — would add a `clinics` table and clinic-scoped foreign keys on doctors/availability/appointments. Deferred: V1 assumes a single clinic.
3. **Timezone-per-clinic** — would add per-clinic timezone metadata and generalize the single-timezone (Asia/Kolkata) rule. Deferred: V1 fixes one clinic timezone.
4. **More advanced appointment scheduling** — recurring appointment series, waitlists, reschedule chains, buffer times between appointments. Deferred: V1 supports single flat bookings with weekly availability windows.
5. **Payment processing** — `invoices`, `payments`, `payment_methods` tables; fee settlement against `consultation_fee_snapshot`. Deferred: V1 stores fees only.
6. **Richer medical records** — `prescriptions`, `medications`, `medical_records`, `diagnoses`, `insurance_claims` tables. Deferred: V1 keeps free-text `allergies` and appointment `notes` only.
7. **Notifications** — `notifications`, `notification_templates` tables plus delivery channels. Deferred: V1 has no notification persistence.
8. **Reviews/ratings** and **analytics events** tables. Deferred.
9. **Partitioning & read replicas** — appointments table by date range; read scaling. Revisit at volume.

## Future Migration Considerations

### Schema Evolution
1. **Version Control**: Use Flyway migration files
2. **Backward Compatibility**: Add columns nullable initially
3. **Data Migration**: Scripts for major changes

## Design Validation Summary

### Strengths
1. **Clear Separation**: Authentication vs profile data
2. **Audit Trail**: Full history of appointment status changes
3. **Flexibility**: Supports multiple roles per user
4. **Performance**: Indexes justified by concrete query patterns only
5. **Data Integrity**: Foreign keys, deterministic CHECKs, and explicit application-layer rules for what SQL cannot express
6. **Concurrency**: Doctor-row locking protocol eliminates double-booking races

### Risks Addressed
1. **Appointment Conflicts**: Lock-ordered transactional protocol
2. **Timezones**: Finalized split — clinic-local appointments, UTC audit timestamps
3. **Deletion**: Consistent soft deletion on users, doctors, patients, specialties
4. **Money**: Exact `DECIMAL` types plus booking-time fee snapshots
5. **Status Safety**: VARCHAR + CHECK instead of ENUM; workflow consistency in the service layer

### Next Steps
1. Create Flyway migration scripts for this schema
2. Implement JPA entities matching this design
3. Add database configuration to Spring Boot
4. Write repository interfaces for data access
5. Create test data for development

---
**Design Complete:** Approved V1 design. Ready for implementation in Step 4.3 (Database Migration).
