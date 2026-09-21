# Service Layer: Appointment Booking Business Logic

A beginner-friendly walkthrough of the service layer introduced in **Step 12**, which turns
the JPA entities and repositories into real clinic business rules.

---

## The Big Picture

```
HTTP request (later: controller)
    ↓
AppointmentService        ← business rules live HERE
    ↓
Spring Data repositories  ← only read/write rows
    ↓
Hibernate / JPA
    ↓
MySQL (Flyway-owned schema)
```

```
com.medbook.service
├── AppointmentService                       ← the business logic
├── AppointmentNotFoundException             ← booking ID does not exist
├── AppointmentConflictException             ← doctor already booked in that slot
├── InvalidAppointmentStatusException        ← impossible status change (e.g. cancel twice)
├── DoctorNotFoundException
├── DoctorInactiveException
├── DoctorUnavailableException
├── PatientNotFoundException
└── PatientInactiveException
```

---

## Why Does a Service Layer Exist?

Repositories answer "how do I store and fetch rows?". They cannot answer
"is this booking allowed?".

Three separate questions are involved in booking an appointment:

| Question | Where it belongs |
|---|---|
| Which rows exist? | repository |
| Is this booking allowed? | **service** |
| Is this data shaped correctly (types, NULLs, allowed status strings)? | database constraints (schema) + entity mapping |

`AppointmentService.createAppointment(...)` is the only place that knows the booking
sequence. Every future caller (REST controller, admin tool, background job) gets the same
rules for free, because the rules are not written inside the controller.

A service layer also gives us a single place to:

- raise meaningful errors (`DoctorInactiveException` instead of "500 Internal Server Error"),
- open a transaction that spans several writes,
- keep the same change atomic (all or nothing),
- be tested without HTTP.

---

## Why Do Transactions Matter Here?

Booking writes **two** rows:

1. the appointment (`appointments`),
2. the first audit row (`appointment_status_history`, status `SCHEDULED`).

If the appointment were inserted and the history insert failed, the clinic would have an
appointment that no audit trail ever recorded. A database transaction prevents that: the
two inserts travel together and either both commit or both roll back.

`AppointmentService` marks its public methods with `@Transactional`
(`org.springframework.transaction.annotation.Transactional`):

```java
@Transactional
public Appointment createAppointment(Long patientId, Long doctorId,
                                     LocalDate appointmentDate,
                                     LocalTime startTime, LocalTime endTime) { ... }
```

Spring starts a transaction before the method body and commits it after the method returns.
If a `RuntimeException` (any of our service exceptions, or an `IllegalArgumentException`)
escapes the method, Spring rolls the transaction back instead of committing.

Rules of thumb used in this project:

- the service method owns the transaction — never the repository method, never the entity,
- an exception is the natural way to abort a transaction,
- only the service knows where the transaction boundary should be.

---

## Why `SELECT ... FOR UPDATE`?

Two receptionists can click "Book" at the same second. Both requests run the overlap check
("is 10:00 free?"), both see "free", and both insert — a double booking. The overlap check
is a **read-then-write race**.

A pessimistic row lock closes that window:

```java
// DoctorRepository
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT d FROM Doctor d WHERE d.id = :id")
Optional<Doctor> findByIdForUpdate(@Param("id") Long id);
```

The first transaction to run this query takes an exclusive lock on that doctor row
(MySQL: `SELECT ... FROM doctors WHERE id = ? FOR UPDATE`). A second transaction that tries
to lock the same doctor **waits** until the first one commits or rolls back. Because the
overlap check now happens *inside* the lock, the second booking sees the first one's
appointment and is rejected with `AppointmentConflictException`.

Important details:

- the lock only exists inside a transaction, which is why the locking query is called from a
  `@Transactional` service method — the lock is released when that transaction ends,
- the lock is on the **doctor row** (one row per booking), not on the whole table, so
  bookings for different doctors still run in parallel,
- `findByIdForUpdate` is used in two places: `createAppointment` (lock the doctor being
  booked) and `cancelAppointment` (lock the appointment being changed),
- this is deliberately **not** a CHECK constraint: SQL cannot express "no two rows of this
  table may overlap in time" without triggers or extra tables, and the task for this step
  keeps the schema untouched.

---

## The Booking Flow Step by Step

`AppointmentService.createAppointment(patientId, doctorId, date, startTime, endTime)`:

| # | Step | Code |
|---|---|---|
| 1 | open transaction | `@Transactional` |
| 2 | lock the doctor row | `doctorRepository.findByIdForUpdate(doctorId)` |
| 3 | doctor must exist | else `DoctorNotFoundException` |
| 4 | doctor must be active | else `DoctorInactiveException` |
| 5 | patient must exist / be active | else `PatientNotFoundException` / `PatientInactiveException` |
| 6 | date and times must make sense | else `IllegalArgumentException` |
| 7 | window must be inside availability | else `DoctorUnavailableException` |
| 8 | no clash with existing appointments | else `AppointmentConflictException` |
| 9 | insert appointment (+ fee snapshot) | `appointmentRepository.save(...)` |
| 10 | insert first history row | `statusHistoryRepository.save(...)` |
| 11 | commit | Spring commits on return |

`cancelAppointment(appointmentId, reason)` follows the same shape:

| # | Step | Code |
|---|---|---|
| 1 | reason must not be blank | else `IllegalArgumentException` |
| 2 | lock the appointment row | `appointmentRepository.findByIdForUpdate(id)` |
| 3 | appointment must exist | else `AppointmentNotFoundException` |
| 4 | status must still be changeable | else `InvalidAppointmentStatusException` |
| 5 | set `CANCELLED` + store the reason | `appointment.setStatus(...)`, `setCancellationReason(...)` |
| 6 | append history row `CANCELLED` | `statusHistoryRepository.save(...)` |

---

## How Availability Validation Works

Availability lives in `doctor_availability`, one row per weekly time window:

| Column | Meaning | Used for |
|---|---|---|
| `doctor_id` | which doctor | filter |
| `day_of_week` | weekday of the pattern | filter |
| `start_time` / `end_time` | the bookable window inside that day | containment check |
| `is_recurring` | repeats weekly vs. one-off exception | rule choice |
| `effective_from` | first date the row applies | lower bound (NULL = no bound) |
| `effective_until` | last date the row applies | upper bound (NULL = no bound) |

The service checks that **at least one** row for the doctor covers the booking:

```java
boolean covered = slots.stream()
        .anyMatch(slot -> coversAppointment(slot, appointmentDate, startTime, endTime));
if (!covered) {
    throw new DoctorUnavailableException(doctor.getId(), appointmentDate, startTime, endTime);
}
```

`coversAppointment(...)` requires two things:

1. **Time containment** — `startTime >= slot.startTime` and `endTime <= slot.endTime`.
   A booking may not stick out of the availability window, so 09:00-12:00 availability does
   not allow 11:30-12:30. Several rows can exist per weekday (morning and evening clinics).
2. **Date rule**
   - *Recurring* (`is_recurring = TRUE`): the pattern repeats every week; the date must fall
     between `effective_from` and `effective_until` when those columns are set, and any NULL
     simply means "no bound".
   - *Non-recurring* (`is_recurring = FALSE`): a one-off exception, so it matches only the
     single date stored in `effective_from` (or `effective_until` if only that column is
     set). The same weekday one week later does **not** match.

### Weekday numbering (easy to get wrong)

| | Sunday | Monday | Tuesday | ... | Saturday |
|---|---|---|---|---|---|
| MySQL `day_of_week` | 0 | 1 | 2 | ... | 6 |
| Java `DayOfWeek.getValue()` | 7 | 1 | 2 | ... | 6 |

The service converts before querying, because `findByDoctorIdAndDayOfWeek` sends the value
straight to SQL:

```java
private static byte toMysqlDayOfWeek(LocalDate date) {
    int javaDayOfWeek = date.getDayOfWeek().getValue(); // 1 = Monday ... 7 = Sunday
    return (byte) (javaDayOfWeek == JAVA_SUNDAY ? 0 : javaDayOfWeek);
}
```

Tests cover both ends of the mapping (`0` = Sunday and `1` = Monday), because a mismatch
here silently rejects valid bookings or accepts invalid ones.

---

## How Conflict Checking Works

A requested window clashes when an existing appointment for the same doctor and date
overlaps it:

```sql
existing.start_time < proposed.end_time
AND existing.end_time > proposed.start_time
```

```java
@Query("SELECT a FROM Appointment a WHERE a.doctor.id = :doctorId " +
       "AND a.appointmentDate = :appointmentDate " +
       "AND a.status <> :excludedStatus " +
       "AND a.startTime < :endTime " +
       "AND a.endTime > :startTime")
List<Appointment> findConflictingAppointments(Long doctorId, LocalDate appointmentDate,
                                             LocalTime startTime, LocalTime endTime,
                                             AppointmentStatus excludedStatus);
```

Design notes:

- **Back-to-back is allowed.** 10:00-11:00 and 11:00-12:00 do not clash, because
  `10:00 < 12:00` is true but `11:00 > 11:00` is false.
- **Cancelled appointments release the slot.** The service passes
  `AppointmentStatus.CANCELLED` as `excludedStatus`, so a cancelled 10:00 slot can be
  re-booked. The status is a typed parameter instead of a string literal, so JPQL compares
  it as a real enum.
- **Only blocking statuses matter.** `SCHEDULED` and `CONFIRMED` still occupy the doctor's
  time; `CANCELLED` does not. `COMPLETED`/`NO_SHOW` are past appointments, so a new future
  booking on the same date/time is prevented by the same overlap rule rather than by special
  cases.
- The check is a normal `SELECT`; correctness under concurrency comes from the doctor row
  lock taken before it (see above), not from the query itself.

---

## How Status History Is Maintained

`appointment_status_history` is an **append-only audit trail**:

- one row per status change,
- `appointment_id` links back to the appointment,
- `changed_at` is filled by the database (`DEFAULT CURRENT_TIMESTAMP(3)`),
- `changed_by_user_id` is optional (authentication does not exist yet, so it is NULL today).

Every status change goes through one private helper:

```java
private void recordStatusChange(Appointment appointment, AppointmentStatus status, String notes) {
    AppointmentStatusHistory history = new AppointmentStatusHistory(appointment, status);
    history.setNotes(notes);
    statusHistoryRepository.save(history);
}
```

Rules the service obeys:

- the appointment row keeps the *current* status, the history table keeps the *story*,
- history rows are never updated or deleted — cancelling an appointment adds a row, it does
  not rewrite the creation row,
- the first row is always `SCHEDULED`, because `createAppointment` writes the appointment and
  its first history row in the same transaction,
- the cancellation reason is stored on the appointment (`cancellation_reason`, required by a
  schema CHECK constraint) **and** as the history row's `notes`, so the audit trail explains
  itself.

Example trail for one appointment:

| id | appointment_id | status | notes | changed_at |
|---|---|---|---|---|
| 1 | 42 | SCHEDULED | Appointment created | 2026-09-21 09:15:02.117 |
| 2 | 42 | CANCELLED | Patient rescheduled the visit | 2026-09-21 09:15:40.882 |

---

## Which Status Changes Are Implemented?

Only the transitions that are clearly justified today:

- `SCHEDULED → CANCELLED` and `CONFIRMED → CANCELLED` are allowed,
- cancelling twice is rejected (`InvalidAppointmentStatusException`),
- cancelling a `COMPLETED` or `NO_SHOW` appointment is rejected (both are terminal),
- confirming (`CONFIRMED`) and completing (`COMPLETED` / `NO_SHOW`) are **not** implemented
  in this step: the schema has `confirmed_at` / `completed_at` columns for them, but no
  service method exists yet, and inventing those flows now would be guesswork.

---

## Why Business Rules Should Not Live in Controllers

A controller's job is narrow: read the HTTP request, call the service, translate the result
(or the exception) back into an HTTP response. Putting the booking rules there causes real
problems:

1. **Duplication and drift.** A second entry point (admin UI, mobile app, background job)
   would need the same checks copy-pasted. The copies drift apart.
2. **No transaction boundary of its own.** If the controller opened the transaction, the
   rules would only be safe when called through HTTP. Calling the logic from a test or batch
   job would silently lose the lock and the atomicity.
3. **Hard to test.** Controller tests drag in HTTP mocking; service tests can call methods
   directly against a real database.
4. **Bad error semantics.** A controller should map `DoctorInactiveException` to HTTP 409
   with a message. Deciding *whether* the doctor is inactive is a business decision, and it
   belongs to the service.

So the future booking controller should be only a few lines:

```java
// Step 13+ (not written yet)
@PostMapping
public ResponseEntity<AppointmentResponse> book(@RequestBody BookingRequest request) {
    Appointment appointment = appointmentService.createAppointment(
            request.patientId(), request.doctorId(),
            request.date(), request.startTime(), request.endTime());
    return ResponseEntity.status(HttpStatus.CREATED).body(AppointmentResponse.from(appointment));
}
```

No `if` statements, no `@Transactional`, no overlap queries.

---

## Service Logic vs Database Constraints

Both layers guard the data. They are not duplicates — they answer different questions.

| Concern | Database (Flyway V1 schema) | Service (AppointmentService) |
|---|---|---|
| Status must be one of five values | `CHECK (status IN (...))` | `AppointmentStatus` enum in the method signature |
| Cancelled appointment needs a reason | `CHECK (status != 'CANCELLED' OR cancellation_reason IS NOT NULL)` | refuses a blank reason before saving |
| Weekly pattern is sane | `CHECK (day_of_week BETWEEN 0 AND 6)`, `CHECK (start_time < end_time)`, `CHECK (effective_from <= effective_until)` | weekday conversion, window containment |
| Referenced rows exist | `FOREIGN KEY` on patient/doctor/appointment | looks IDs up, raises Not-Found/Inactive exceptions |
| Fee is not negative | `CHECK (consultation_fee_snapshot >= 0)` | snapshots `doctor.consultationFee` at booking time |
| No two appointments overlap | **cannot be expressed** without triggers or extra tables | overlap query + doctor row lock |
| Booking must be inside availability | **cannot be expressed** (cross-row logic over two tables) | `ensureDoctorIsAvailable(...)` |
| Who may book for whom | not expressible yet (requests carry no identity) | will arrive with authentication |

Two practical guidelines used in this project:

- **Never rely on the service alone to protect the data** — database constraints are the last
  line of defence if a future script or manual SQL bypasses the service.
- **Never try to express business decisions in the schema** — the schema is versioned by
  Flyway; rules that change weekly (opening hours, cancellation policy) belong in Java, where
  they can change without a migration.

---

## Exceptions Raised by the Service

| Exception | Thrown when | Future HTTP mapping (Step 13+) |
|---|---|---|
| `DoctorNotFoundException` | no doctor row for that ID | 404 |
| `DoctorInactiveException` | doctor exists but `is_active = FALSE` | 409 / 403 |
| `PatientNotFoundException` | no patient row for that ID | 404 |
| `PatientInactiveException` | patient exists but `is_active = FALSE` | 409 / 403 |
| `DoctorUnavailableException` | window not covered by availability | 409 |
| `AppointmentConflictException` | doctor already booked in that window | 409 |
| `AppointmentNotFoundException` | cancelling an unknown appointment | 404 |
| `InvalidAppointmentStatusException` | illegal status transition | 409 |
| `IllegalArgumentException` | malformed input (past date, `start >= end`, blank reason) | 400 |

All of them extend `RuntimeException`, so Spring rolls the transaction back automatically
when they escape a `@Transactional` method.

---

## How This Is Tested

Two integration test classes run against the real MySQL database (no H2), following the
existing repository-test style.

**`AppointmentServiceTest`** — 25 tests, class-level `@Transactional`, so every test is rolled
back by Spring. It covers successful booking, the fee snapshot, initial `SCHEDULED` status,
missing and inactive doctors/patients, past dates, invalid time windows, availability
containment, the MySQL weekday conversion (Sunday = 0 and Monday = 1), `effective_from` /
`effective_until` bounds, one-off (non-recurring) slots, overlap rejection, back-to-back
slots, re-booking after cancellation, every cancellation outcome, and that a rejected booking
writes no rows.

**`AppointmentServiceTransactionTest`** — 7 tests, deliberately **not** `@Transactional`,
because it must observe real commit/rollback behaviour:

- successful booking really commits appointment + history,
- cancellation really commits `CANCELLED`, the reason, and a second history row,
- a transaction that fails *after* the booking inserts rolls both rows back (nothing left),
- a rejected booking leaves no residue,
- `@Transactional` is present on the service methods and `@Lock(PESSIMISTIC_WRITE)` on both
  `findByIdForUpdate` queries,
- **lock proof:** two real transactions run on two threads; while the first holds the doctor
  row lock the second cannot finish, and it completes as soon as the first commits. If the
  `FOR UPDATE` lock were missing, the second transaction would finish immediately and the
  test would fail.

Its fixtures are committed on purpose (a second transaction cannot see uncommitted rows) and
are deleted again in `@AfterEach` in foreign-key order, so the database is left clean.

---

## Known Limitations / Next Steps

- `changed_by_user_id` is left NULL: identifying the caller belongs to the authentication
  step, which is explicitly out of scope here.
- `confirmed_at` / `completed_at` stay unused until a confirm/complete transition is added.
- `updated_at` has no `ON UPDATE CURRENT_TIMESTAMP` in the V1 schema, so it does not change on
  `UPDATE`; changing that would need a new Flyway migration (out of scope, schema frozen).
- No REST controller, DTO, or exception handler exists yet — the service is the public
  boundary for now (Step 13+).
- The service runs one availability `SELECT` per booking. Caching or pre-loading slots is an
  optimisation to consider only if measurements demand it.
