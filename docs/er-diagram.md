# MedBook Entity Relationship Diagram

V1 design — reflects all Approved V1 Design Decisions (see docs/database-design.md). Diagram only; no tables have been created.

```mermaid
erDiagram
    USERS {
        BIGINT UNSIGNED id PK "Primary key"
        VARCHAR(255) email UK "Unique email"
        VARCHAR(255) password_hash "Hashed password (one-way)"
        VARCHAR(100) first_name "First name"
        VARCHAR(100) last_name "Last name"
        VARCHAR(20) phone_number "Contact phone"
        BOOLEAN is_active "Account active (soft delete)"
        BOOLEAN is_locked "Login locked"
        TINYINT UNSIGNED failed_login_attempts "Failed attempts"
        DATETIME last_login_at "Last login (UTC)"
        DATETIME created_at "Creation timestamp (UTC)"
        DATETIME updated_at "Update timestamp (UTC)"
    }

    ROLES {
        BIGINT UNSIGNED id PK "Primary key"
        VARCHAR(50) name UK "ADMIN | DOCTOR | PATIENT (seed-enforced)"
        VARCHAR(255) description "Role description"
    }

    USER_ROLES {
        BIGINT UNSIGNED user_id PK,FK "References users.id"
        BIGINT UNSIGNED role_id PK,FK "References roles.id"
        DATETIME assigned_at "Assignment timestamp (UTC)"
    }

    SPECIALTIES {
        BIGINT UNSIGNED id PK "Primary key"
        VARCHAR(100) name UK "Specialty name"
        TEXT description "Detailed description"
        BOOLEAN is_active "Active status (soft delete)"
        DATETIME created_at "Creation timestamp (UTC)"
    }

    DOCTORS {
        BIGINT UNSIGNED id PK "Primary key"
        BIGINT UNSIGNED user_id UK,FK "References users.id"
        BIGINT UNSIGNED specialty_id FK "Exactly one primary specialty (V1)"
        VARCHAR(50) medical_license_number UK "License number"
        TINYINT UNSIGNED years_experience "Years of practice"
        TEXT bio "Professional biography"
        DECIMAL(10,2) consultation_fee "Current appointment fee"
        BOOLEAN is_active "Active status (soft delete)"
        VARCHAR(500) profile_picture_url "Profile image URL"
        DATETIME created_at "Creation timestamp (UTC)"
        DATETIME updated_at "Update timestamp (UTC)"
    }

    PATIENTS {
        BIGINT UNSIGNED id PK "Primary key"
        BIGINT UNSIGNED user_id UK,FK "References users.id"
        DATE date_of_birth "Birth date (validated in app layer)"
        VARCHAR(255) address_line1 "Primary address"
        VARCHAR(255) address_line2 "Secondary address"
        VARCHAR(100) city "City"
        VARCHAR(50) state_province "State/Province"
        VARCHAR(20) postal_code "Postal code"
        VARCHAR(50) country "Country"
        VARCHAR(200) emergency_contact_name "Emergency contact"
        VARCHAR(20) emergency_contact_phone "Emergency phone"
        BOOLEAN has_insurance "Insurance status"
        VARCHAR(100) insurance_provider "Required when has_insurance"
        VARCHAR(50) insurance_policy_number "Required when has_insurance"
        TEXT allergies "Known allergies"
        BOOLEAN is_active "Active status (soft delete)"
        DATETIME created_at "Creation timestamp (UTC)"
        DATETIME updated_at "Update timestamp (UTC)"
    }

    DOCTOR_AVAILABILITY {
        BIGINT UNSIGNED id PK "Primary key"
        BIGINT UNSIGNED doctor_id FK "References doctors.id"
        TINYINT UNSIGNED day_of_week "0-6 (Sunday=0)"
        TIME start_time "Window start (clinic-local, Asia/Kolkata)"
        TIME end_time "Window end (clinic-local, Asia/Kolkata)"
        BOOLEAN is_recurring "Recurring availability"
        DATE effective_from "Start date"
        DATE effective_until "End date"
        DATETIME created_at "Creation timestamp (UTC)"
    }

    APPOINTMENTS {
        BIGINT UNSIGNED id PK "Primary key"
        BIGINT UNSIGNED patient_id FK "References patients.id"
        BIGINT UNSIGNED doctor_id FK "References doctors.id"
        DATE appointment_date "Appointment date (clinic-local, Asia/Kolkata)"
        TIME start_time "Start time (clinic-local)"
        TIME end_time "End time (clinic-local)"
        VARCHAR(500) reason "Appointment reason"
        TEXT notes "Doctor notes"
        VARCHAR(20) status "SCHEDULED|CONFIRMED|COMPLETED|CANCELLED|NO_SHOW (CHECK)"
        VARCHAR(255) cancellation_reason "If cancelled"
        DECIMAL(10,2) consultation_fee_snapshot "Fee at booking time"
        DATETIME created_at "Creation timestamp (UTC)"
        DATETIME updated_at "Update timestamp (UTC)"
        DATETIME confirmed_at "Confirmation timestamp (UTC)"
        DATETIME completed_at "Completion timestamp (UTC)"
    }

    APPOINTMENT_STATUS_HISTORY {
        BIGINT UNSIGNED id PK "Primary key"
        BIGINT UNSIGNED appointment_id FK "References appointments.id"
        VARCHAR(20) status "Status value at change (CHECK)"
        BIGINT UNSIGNED changed_by_user_id FK "References users.id (nullable)"
        VARCHAR(500) notes "Change notes"
        DATETIME changed_at "Change timestamp (UTC)"
    }

    %% Relationships
    USERS ||--o{ USER_ROLES : "assigned"
    ROLES ||--o{ USER_ROLES : "assigned"

    USERS ||--o| DOCTORS : "has"
    USERS ||--o| PATIENTS : "has"

    SPECIALTIES ||--o{ DOCTORS : "categorizes"

    DOCTORS ||--o{ DOCTOR_AVAILABILITY : "has"
    DOCTORS ||--o{ APPOINTMENTS : "scheduled"

    PATIENTS ||--o{ APPOINTMENTS : "booked"

    APPOINTMENTS ||--o{ APPOINTMENT_STATUS_HISTORY : "tracks"

    USERS ||--o{ APPOINTMENT_STATUS_HISTORY : "changed"
```

## Cardinality Legend
- `||--||` : One-to-One (mandatory both ends)
- `||--o|` : One-to-Zero-or-One (parent mandatory, child side optional)
- `||--o{` : One-to-Many (one parent, zero-or-more children)
- `o{` : Zero or more
- `||` : Exactly one
- `o|` : Zero or one

Note: MySQL/mermaid many-to-one with a nullable FK is expressed from the parent side as one-to-many (`||--o{`); each history row references at most one user, so USERS ↔ APPOINTMENT_STATUS_HISTORY is **not** many-to-many.

## Relationship Details

### User Management
1. **User ↔ Role**: Many-to-Many via `user_roles` junction table
   - One user can have multiple roles (e.g., doctor who is also a patient)
   - One role can be assigned to many users
   - Allowed role values (ADMIN, DOCTOR, PATIENT) enforced at application/seed-data level — no DB enum

2. **User ↔ Doctor**: One-to-Zero-or-One (`USERS ||--o| DOCTORS`)
   - Each doctor has exactly one user account (mandatory, FK `doctors.user_id` UNIQUE NOT NULL)
   - Each user account has **at most one** doctor record (may have none)

3. **User ↔ Patient**: One-to-Zero-or-One (`USERS ||--o| PATIENTS`)
   - Each patient has exactly one user account (mandatory, FK `patients.user_id` UNIQUE NOT NULL)
   - Each user account has **at most one** patient record (may have none)
   - A single user may hold both a doctor record and a patient record (via multiple roles)

### Medical Domain
4. **Specialty ↔ Doctor**: One-to-Many
   - Each doctor belongs to exactly one primary specialty (V1 decision)
   - Each specialty can have many doctors (or none)

5. **Doctor ↔ Availability**: One-to-Many
   - Each doctor can have multiple non-overlapping windows per weekday
   - Each availability window belongs to exactly one doctor
   - Overlap validation in the service layer (MySQL has no partial unique indexes)

6. **Doctor ↔ Appointment**: One-to-Many
   - Each doctor can have multiple appointments
   - Each appointment is with exactly one doctor

7. **Patient ↔ Appointment**: One-to-Many
   - Each patient can have multiple appointments
   - Each appointment is for exactly one patient

### Appointment Lifecycle
8. **Appointment ↔ Status History**: One-to-Many
   - Each appointment can have multiple status changes
   - Each status change belongs to exactly one appointment
   - The service creates an initial SCHEDULED history record at booking (concurrency protocol step 5)

9. **User ↔ Status History**: One-to-Many from users (`USERS ||--o{ APPOINTMENT_STATUS_HISTORY`)
   - Each history row references at most one user (`changed_by_user_id` nullable, ON DELETE SET NULL)
   - Not many-to-many: a history row has at most one actor

## Key Design Patterns

### Primary Key Strategy
- All tables use `BIGINT UNSIGNED AUTO_INCREMENT` as primary key
- Natural keys (email, license) have unique constraints
- `user_roles` uses a composite PK with no duplicate unique constraint
- Consistent `id` naming across all tables

### Foreign Key Strategy
- Core entities use `ON DELETE RESTRICT` to prevent accidental deletion
- Dependent data uses `ON DELETE CASCADE` for cleanup
- Optional references use `ON DELETE SET NULL`
- MySQL InnoDB auto-indexes FK columns; no redundant duplicates are created

### Time Relationships
- **Availability**: Weekly recurring windows, clinic-local (Asia/Kolkata), with optional effective date ranges
- **Appointments**: Specific clinic-local date/time combinations
- **History**: UTC `changed_at` timestamps

### Status Representation
- `VARCHAR(20)` + CHECK constraint (not ENUM) on both `appointments.status` and `appointment_status_history.status`
- Allowed: SCHEDULED, CONFIRMED, COMPLETED, CANCELLED, NO_SHOW
- Java application enum provides type safety in backend code

### Fee Snapshot
- `doctors.consultation_fee` = current fee
- `appointments.consultation_fee_snapshot` = fee preserved at booking time

---

## Database Growth Considerations

### Expected Volume
1. **Users**: Thousands to tens of thousands
2. **Doctors**: Hundreds to thousands
3. **Patients**: Thousands to hundreds of thousands
4. **Appointments**: Millions over time
5. **Status History**: Multiple entries per appointment

### Index Strategy
1. **Uniques double as lookups**: email, license, doctor user_id, patient user_id
2. **Composite indexes**: doctor-date, patient-date for common appointment queries
3. **No speculative indexes**: added only when concrete queries justify them

### Partitioning Strategy (Future)
1. **Appointments**: By date range (monthly/yearly partitions) — deferred
2. **Status History**: By appointment_id range — deferred
3. **Consideration**: MySQL 8.0+ supports native partitioning

---
**Diagram Complete:** V1 representation of the MedBook database schema design (approved decisions applied).
