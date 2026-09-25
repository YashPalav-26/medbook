# Doctor Portal & Appointment Status Lifecycle (Step 21)

This document explains how a doctor uses MedBook to manage their appointments —
confirm, complete, mark a no-show — and how the backend guarantees that only the
right doctor can move the *right* appointment through the *right* states.

---

## 1. The doctor role

MedBook has three roles: `PATIENT`, `DOCTOR`, and `ADMIN`. A doctor is a `User`
account (they log in with email + password, exactly like patients) plus a `Doctor`
profile (specialty, license number, fee…) that admin accounts create in the
admin area (Step 19).

A doctor does **not** book appointments. Patients book them, and the booking
flow assigns the chosen doctor. The doctor's job is to *run* the visit:

- see the appointments patients booked with them
- confirm the visit will happen
- mark it completed after the visit
- mark it no-show if the patient never arrived

## 2. The status lifecycle

An appointment is always in exactly one of five states
(`AppointmentStatus` enum, enforced by a CHECK constraint in the schema):

```
SCHEDULED ──▶ CONFIRMED ──▶ COMPLETED
    │             │
    │             └──▶ NO_SHOW
    └──▶ CANCELLED ◀── (CONFIRMED can also be cancelled)
```

Valid transitions, and who may perform them:

| From | To | Who | How |
|---|---|---|---|
| SCHEDULED | CONFIRMED | assigned doctor | `POST /appointments/{id}/confirm` |
| SCHEDULED | CANCELLED | owning patient or assigned doctor | existing `POST /appointments/{id}/cancel` |
| CONFIRMED | COMPLETED | assigned doctor | `POST /appointments/{id}/complete` |
| CONFIRMED | NO_SHOW | assigned doctor | `POST /appointments/{id}/no-show` |
| CONFIRMED | CANCELLED | owning patient or assigned doctor | existing cancel |

Everything else is rejected **by the server** with HTTP 409 — for example
`COMPLETED → CONFIRMED`, `CANCELLED → CONFIRMED`, completing a visit that was
never confirmed, or acting on any terminal state. Terminal states
(`COMPLETED`, `CANCELLED`, `NO_SHOW`) accept no transitions at all.

## 3. Server-side authorization

The frontend hides buttons a role cannot use, but that is **courtesy, not
security**. Anyone with `curl` can call any endpoint. The backend enforces,
inside the service (never the controller):

1. **Authenticated?** No valid JWT → 401 (Spring Security, before the service runs).
2. **Right role?** Only `ROLE_DOCTOR` may confirm/complete/no-show. A patient or
   admin calling these endpoints gets **403** — admins deliberately do *not*
   receive doctor workflow powers; that policy decision was never made, so it is
   refused rather than silently granted.
3. **Right doctor?** The service loads the appointment and compares
   `appointment.doctor.user.id` with the authenticated caller's user ID.
   A different doctor gets **403**. Ownership is checked *before* status, so a
   non-owner cannot even learn whether the appointment is cancellable from the error.

The caller's identity always comes from the JWT (`CurrentUserProvider` →
`SecurityContext`), never from the request body. There is no "doctorId" field
to forge.

## 4. What the service does on each transition

Every doctor transition method (`confirmAppointmentForCurrentUser`,
`completeAppointmentForCurrentUser`, `markNoShowForCurrentUser`) follows the
same atomically-transactional recipe:

1. Resolve the authenticated doctor from the JWT; reject non-doctors (403).
2. Load the appointment **with a pessimistic row lock**
   (`SELECT … FOR UPDATE` via `findByIdForUpdate`) — see §7.
3. Check ownership (403 for the wrong doctor, 404 if the row doesn't exist).
4. Check the current status against the transition table (409 when invalid).
5. Update the status (and `confirmed_at` / `completed_at` timestamps where relevant).
6. Append an `appointment_status_history` row.
7. Commit — all of it, or none of it.

## 5. The audit trail: `changed_by_user_id`

Every successful transition writes a history row recording:

- the appointment,
- the **new** status,
- **who** changed it — the real authenticated doctor's user ID, resolved from
  the JWT (never accepted from the client, never fabricated), and
- short notes (e.g. "Appointment confirmed by doctor").

This is why the database can answer "who confirmed this visit?" with a real
account, years later. Rows with a NULL actor only occur where no caller exists
(the initial SCHEDULED row written by an internal, non-HTTP booking path).

## 6. The REST endpoints

| Endpoint | Role | Success | Errors |
|---|---|---|---|
| `POST /api/v1/appointments/{id}/confirm` | assigned DOCTOR | 200 + `AppointmentResponse` | 401 / 403 / 404 / 409 |
| `POST /api/v1/appointments/{id}/complete` | assigned DOCTOR | 200 + `AppointmentResponse` | 401 / 403 / 404 / 409 |
| `POST /api/v1/appointments/{id}/no-show` | assigned DOCTOR | 200 + `AppointmentResponse` | 401 / 403 / 404 / 409 |

None of them take a body. 409 is used for invalid transitions because it matches
the project's existing business-conflict convention (double-booking, cancelling
a completed visit, duplicate availability windows — all 409). The endpoints
return the same DTO as every other appointment endpoint; JPA entities are never
exposed.

`GET /api/v1/appointments/me` needed no changes: it has been role-aware since
Step 17 and already returns only appointments assigned to the authenticated doctor.

## 7. Transactions and locking

Two doctors (or two browser tabs) could click "Confirm" at the same moment.
Without protection, both would read SCHEDULED, both would pass validation, and
the audit trail would contain two CONFIRMED rows for one visit. The pessimistic
row lock (`PESSIMISTIC_WRITE`, `SELECT … FOR UPDATE`) makes the second
transaction wait; when it proceeds, it re-reads the *committed* status
(CONFIRMED), fails validation, and returns 409. The outcome is exactly one
transition and exactly one audit row — verified by a dedicated concurrency test
that races two real HTTP requests.

## 8. The doctor frontend

The portal lives inside the **same** MedBook design system — same AppShell,
cards, badges, toasts and typography as the patient area (no separate visual language):

- **`/doctor` (Doctor Dashboard)** — "Welcome back, Dr. …", today's appointments,
  and upcoming ones. All data from `GET /appointments/me`; no fabricated KPIs.
  Empty states instead of fake numbers.
- **`/doctor/appointments`** — the doctor's list with status filter tabs
  (real counts) and responsive cards that link into the shared detail page.
- **`/appointments/:id`** — one shared detail view for every role. When the
  signed-in user is a doctor, the correct lifecycle buttons appear:
  *Confirm* on SCHEDULED, *Complete* + *No-Show* on CONFIRMED, nothing on
  terminal states. Patients instead see cancellation (where allowed).

Navigation in the sidebar is role-aware: doctors get Dashboard + My Appointments
(Practice section), admins keep the admin area, patients keep booking.

## 9. React Query workflow

Three mutations (`useConfirmAppointment`, `useCompleteAppointment`,
`useNoShowAppointment`) wrap the three endpoints. On success they invalidate the
appointment-list cache and write the returned appointment straight into the
detail cache, so every open view updates without a page reload. A toast confirms
the change; errors (409 "invalid transition", 403 "wrong doctor"…) surface from
the shared error normalization in plain language.

## 10. Confirmation dialogs

Each transition opens the standard MedBook modal first, describing the
consequence ("A completed visit cannot be changed afterwards"). The confirm
button shows a spinner while the request runs and the modal displays server
errors if the transition was rejected. Destructive *cancellation* keeps its
existing reason-required dialog from Step 20.

## 11. Route guard

`/doctor/**` is wrapped in a `DoctorRoute` guard that checks the roles the
backend returned at login and shows an "Access denied" card to everyone else.
Like all frontend guards, this is UX only — the backend re-verifies everything.

## 12. Testing strategy

**Backend (integration, real MySQL, real JWTs):** the happy paths (confirm →
complete → no-show), every rejection (wrong doctor 403, patient 403, admin 403,
anonymous 401, invalid transitions 409), the audit trail (three ordered history
rows, `changed_by_user_id` = the real doctor, no rows for rejected transitions),
and a two-thread race proving the row lock serializes concurrent confirms.
The concurrency test follows `AppointmentServiceTransactionTest`'s committed-fixture
pattern (its worker threads cannot see the rolled-back test transaction) and
cleans up after itself.

**Frontend (Vitest + Testing Library):** dashboard/list render with real-shaped
data, empty and error states, role-aware button visibility (doctor sees actions,
patient does not), dialog-driven transitions updating the UI, 409 handling
inside the dialog, and terminal states hiding every action.

**End-to-end:** a real doctor account drives the full lifecycle in the browser —
confirm, verify in MySQL that the history row carries the doctor's real user ID,
complete, attempt an invalid transition, and confirm a second doctor is refused
with 403.
