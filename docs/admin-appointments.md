# Admin Appointments — Clinic Overview and Appointment Management (Step 23)

> Audience: anyone reading the MedBook codebase for the first time — students,
> reviewers, or a new developer joining the project. No prior knowledge of the
> admin area is assumed.

## Why admins need appointment visibility

MedBook separates three jobs: **patients** book visits, **doctors** run them, and
**administrators** run the *clinic*. Before this feature, an administrator could
manage specialties, doctors and availability, but could not see the appointments
those settings produce. Answering a simple question — *"which visits are booked
for tomorrow?"* — meant querying the database directly.

An administrator's job is operational: checking tomorrow's load, finding a
patient's booking when they call support, and spotting which doctor a visit is
with. This feature gives admins **read access to every appointment in the
clinic**, plus exactly one audited support action (cancellation). It deliberately
does **not** turn admins into doctors.

## What was built (the short version)

| Piece | Where |
|---|---|
| Clinic-wide appointment list with filters | `GET /api/v1/admin/appointments` |
| One appointment + full status history | `GET /api/v1/admin/appointments/{id}` |
| Real dashboard metrics | `GET /api/v1/admin/appointments/overview` |
| Audited admin cancellation | `POST /api/v1/admin/appointments/{id}/cancel` |
| List page with filters | `/admin/appointments` |
| Detail page with history timeline | `/admin/appointments/:id` |
| Dashboard with real metrics | `/admin` |

## How the appointment data model works (briefly)

An appointment links one **patient** row and one **doctor** row; each of those
links to a **user** account that carries the human's name. The doctor also links
to a **specialty**. Every status change appends a row to
`appointment_status_history`: the new status, a free-text note, the time, and the
user who caused the change (`changed_by_user_id`). History is append-only —
nothing is ever edited or deleted.

The admin APIs expose only what staff need to see: names, IDs, dates, fees,
statuses. They never expose passwords, role assignments, or raw internal columns.

## The admin appointment API

All endpoints live under `/api/v1/admin/**`, which the security configuration
restricts to the ADMIN role. Anonymous callers get **401** (no token), and
authenticated patients or doctors get **403**. This is enforced before any
controller code runs — the application itself never needs to "check" the role
again.

### Listing with filters

```
GET /api/v1/admin/appointments
GET /api/v1/admin/appointments?date=2026-09-28
GET /api/v1/admin/appointments?status=CONFIRMED
GET /api/v1/admin/appointments?doctorId=4453
GET /api/v1/admin/appointments?date=2026-09-24&status=SCHEDULED&doctorId=4453
```

Every filter is optional and combinable. An omitted filter is simply not applied.
The response is a JSON array; each row contains the appointment's id, date, start
and end time, status, cancellation reason, fee snapshot, **plus the patient's
name, the doctor's name, and the specialty** — the things support staff need to
identify a visit without opening it.

Each row looks like:

```json
{
  "id": 2825,
  "patientId": 4097,
  "patientName": "Mary Lee",
  "doctorId": 4454,
  "doctorName": "Marcus Chen",
  "specialty": "Cardiology",
  "appointmentDate": "2026-09-24",
  "startTime": "11:00",
  "endTime": "11:30",
  "status": "CONFIRMED",
  "cancellationReason": null,
  "consultationFeeSnapshot": 275.00
}
```

### Detail with status history

```
GET /api/v1/admin/appointments/2825
```

Returns the same appointment information **plus** the audit trail:

```json
{
  "appointment": { "...": "as above" },
  "history": [
    {
      "id": 2660,
      "status": "SCHEDULED",
      "notes": "Appointment created",
      "changedByName": "Mary Lee",
      "changedAt": "2026-09-23T06:00:45.082"
    },
    {
      "id": 2663,
      "status": "CONFIRMED",
      "notes": "Appointment confirmed by doctor",
      "changedByName": "Marcus Chen",
      "changedAt": "2026-09-23T06:01:10.673"
    }
  ]
}
```

The history shows **who** did **what** and **when** — by display name, never by
raw account ID, email, or any security internal. An unknown id returns **404**.

### Real dashboard metrics

```
GET /api/v1/admin/appointments/overview
```

```json
{
  "todayAppointments": 5,
  "today": "2026-09-25",
  "statusCounts": {
    "SCHEDULED": 5, "CONFIRMED": 1, "COMPLETED": 0, "CANCELLED": 2, "NO_SHOW": 0
  },
  "totalAppointments": 8
}
```

Every number is computed from live rows with two aggregate queries. There are no
estimated or placeholder metrics — if the project cannot compute a number, the
dashboard does not show it (no fake revenue, growth, or percentages).

## The database queries (why fetch joins)

Open Session In View is disabled (`spring.jpa.open-in-view=false`), which is a
deliberate performance choice: it means any lazy relationship a response needs
must be fetched explicitly, in the repository, inside the transaction.

The admin queries therefore use **JPQL `JOIN FETCH`** — one SQL round trip loads
the appointment *together with* the patient, the patient's user, the doctor, the
doctor's user, and the specialty. Without this, mapping each row would trigger
extra lazy loads (the classic "N+1 query" problem) or fail outright after the
transaction ends. See
`AppointmentRepository.findAdminAppointments` and `findByIdForAdmin`.

The same query handles every filter combination through the
`(:param IS NULL OR ...)` idiom — a `null` parameter means "don't filter on
this". The dashboard overview uses one `GROUP BY status` projection
(`AppointmentStatusCount`) plus a single date count, instead of five separate
count queries.

## The admin service layer

Controllers stay thin; the rules live in `AdminAppointmentService`:

- **Reads** run in `readOnly` transactions and map entities to DTOs
  (`AdminAppointmentResponse`, `AdminAppointmentDetailResponse`,
  `AppointmentStatusHistoryResponse`) — JPA entities are never serialized.
- **Cancellation** reuses the project's existing mutation pattern: resolve the
  caller from the JWT, lock the row with `SELECT ... FOR UPDATE`
  (`findByIdForUpdate`), validate the status transition, apply the change, append
  the history row, and commit — all atomically. Two simultaneous cancels
  serialize on the row lock; the loser sees the winner's CANCELLED status and
  gets **409**.

## Admin cancellation — the one explicitly implemented action

Cancellation was implemented deliberately (and optionally) as a *support
action*: a patient may phone the clinic and ask an administrator to cancel a
visit. The rules:

1. **Explicit authorization.** The route is ADMIN-only by URL. The service
   additionally re-checks the ADMIN role from the JWT (defense in depth).
2. **Confirm modal + required reason.** The UI asks "Cancel this appointment as
   admin?" and refuses to submit without a reason (max 255 chars, mirrored by
   Bean Validation on the backend). Empty reason → **400**.
3. **Same status rules as everyone else.** Only SCHEDULED or CONFIRMED
   appointments can be cancelled. Already-cancelled, completed, or no-show rows
   → **409**. Admins get no extra power over the lifecycle.
4. **Audited.** The history row records the authenticated admin's user id in
   `changed_by_user_id` — verified in tests and against the real database.
5. **Transactional.** Status update + history row commit together; a failure
   writes nothing.

What admins still **cannot** do: confirm, complete, or no-show appointments.
Those remain the assigned doctor's workflow (`POST
/api/v1/appointments/{id}/confirm|complete|no-show`), which requires the DOCTOR
role *and* assignment. The old patient/doctor cancel endpoint
(`/api/v1/appointments/{id}/cancel`) continues to reject admins with **403** —
covered by a dedicated test so the boundary cannot silently regress.

## The frontend

The pages reuse the existing admin design language — `AdminNav`, `PageHeader`,
`Card`, `StatusBadge`, `EmptyState`, `ErrorBox`, `Skeleton`, `Modal`, toasts —
so the area feels like one application, not a bolted-on dashboard.

### List page (`/admin/appointments`)

- **Date, Status, Doctor filters and a Clear Filters button.** Every change
  refetches from the backend — the filter object is part of the React Query key,
  so the server does the filtering (nothing is filtered in JavaScript).
- **Desktop: a table** (ID, patient, doctor, specialty, date, time, status, fee,
  View). **Mobile: the same rows as cards.** The count line ("8 appointments
  found") confirms the server actually re-queried.
- Loading, empty ("no appointments yet" vs "no appointments match these
  filters"), and error states (403 renders a friendly "not allowed" message with
  retry; no stack traces or SQL ever surface) follow the shared primitives.

### Detail page (`/admin/appointments/:id`)

- Visit summary: patient (name + id), doctor (name + id + specialty), date,
  start/end, fee snapshot, cancellation reason when present.
- **Status history timeline**: one entry per lifecycle event with the status
  pill, note, timestamp, and "by <name>" (or "by system (no user recorded)" for
  rows written without an authenticated caller, e.g. old internal operations).
- **Cancel as Admin** button (only for SCHEDULED/CONFIRMED rows) → confirm modal
  with required reason → success toast → React Query invalidation refreshes the
  list and dashboard.
- 404 renders a dedicated "Appointment not found" state; other errors render the
  shared error box with retry.

### Dashboard (`/admin`)

The dashboard now leads with the **Clinic overview** section fed by the real
overview endpoint: today's appointments (with the date it refers to), the
all-time total, and the per-status breakdown. Metrics load with a skeleton, and
load failure shows an error box rather than zeros. The management cards
(Appointments, Specialties, Doctors, Availability) sit below, and the admin nav
gained an "Appointments" entry.

### React Query hooks

`useAdminAppointments(filters)`, `useAdminAppointment(id)`,
`useAdminOverview()`, and the `useAdminCancelAppointment()` mutation live in
`useAdmin.ts` alongside the other admin hooks, using the same
`['admin', 'appointments', ...]` key family. Cancellation invalidates the whole
family — lists (any filter combination may contain the row), details, and the
dashboard counts. No appointment data passes through AuthContext.

## Testing

**Backend — `AdminAppointmentsTest` (23 tests, real MySQL, real JWTs minted with
the app's own `JwtEncoder`):** the full authorization matrix (admin 200 /
patient 403 / doctor 403 / anonymous 401), every filter individually plus a
combined-intersection case, invalid status → 400, detail with participants and
history, actor-name exposure without any account internals, unknown id → 404,
real overview math relative to a baseline, admin cancellation (success, audit
row with the admin's id, 409 on finished/cancelled rows, 400 on blank reason,
404, rollback leaves no history row), and the policy boundary test proving
`/api/v1/appointments/{id}/cancel` still rejects admins. Everything runs in
rolled-back transactions — no residue.

**Frontend — `AdminAppointments.test.tsx` (21 tests):** list rendering
(table-scoped), loading skeleton, empty state, date/status/doctor filters hitting
the API, clear-filters, row navigation, 403 and 500 handling, detail summary,
history timeline with actor names, 404 and 403 states, the cancel flow (modal,
blocked empty submit, required-reason validation, mutation payload, toast), the
cancel button hiding for finished appointments, 409 surfacing inside the modal,
and the dashboard's real metrics, per-status breakdown, error state, and link.

## How to verify it by hand (5 minutes)

1. Log in as `admin@medbook.test`. The **Admin Dashboard** shows real numbers
   (today, total, per-status).
2. Open **Appointments**. All bookings appear with names and specialties. Type a
   date or pick a status/doctor — the count line and rows change (server-side).
3. Click **View** on any row. The detail shows participants, fee, and the status
   timeline with actor names.
4. On a SCHEDULED/CONFIRMED appointment, click **Cancel as Admin**, enter a
   reason, confirm. The status flips to CANCELLED and the timeline records the
   change "by Clinic Admin".
5. Log in as a patient and visit `/admin/appointments` — you get "Access denied"
   and every admin API call would 403 anyway.

## Current limitations

- **No pagination.** The list returns every matching row. Fine for a clinic-scale
  dataset; the endpoint should paginate before any real-world scale.
- **No sorting options.** Newest visits first is fixed.
- **Cancellation only.** Admins cannot edit appointment details (doctor, date,
  time), and cannot confirm/complete/no-show — those stay with the patient and
  doctor respectively.
- **No "actor filter" or reason search** on history — the timeline is visible
  per appointment, not searchable clinic-wide.
- **Names are displayed, not raw account data.** Admins cannot see or manage
  login credentials from this screen (by design).
