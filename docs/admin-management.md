# Admin Management (Step 19)

This document explains the MedBook admin area: what an administrator can manage
(specialties, doctors, availability), how the backend enforces ADMIN-only access,
and how the frontend pages were built to look and behave like the rest of MedBook.

---

## 1. Why admin management exists

Until now, the data the booking flow depends on — specialties, doctors, their
availability — could only be changed by writing SQL by hand. That is exactly how
mistakes happen: a typo in a name, a forgotten availability window, a doctor
deactivated without anyone noticing. Step 19 gives administrators a real browser
interface for this work, with the same validation and authorization rules the rest
of the application lives by.

Full-stack feature parity: every capability below has **both** the backend
implementation and the admin UI, delivered and verified together.

---

## 2. The admin APIs

All routes live under `/api/v1/admin/**` and require the **ADMIN role**:

| Method | Route | Purpose |
|---|---|---|
| GET | `/admin/specialties` | all specialties, active and inactive |
| POST | `/admin/specialties` | create (409 on duplicate name) |
| PUT | `/admin/specialties/{id}` | rename / edit description |
| PATCH | `/admin/specialties/{id}/deactivate` | retire (never delete) |
| GET | `/admin/doctors` | all doctors with account linkage |
| GET | `/admin/doctors/{id}` | one doctor |
| POST | `/admin/doctors` | create doctor **+ login account + DOCTOR role** |
| PUT | `/admin/doctors/{id}` | edit profile (not credentials) |
| PATCH | `/admin/doctors/{id}/deactivate` | remove from discovery, keep history |
| GET | `/admin/doctors/{doctorId}/availability` | all windows of a doctor |
| POST | `/admin/doctors/{doctorId}/availability` | add window |
| PUT | `/admin/availability/{id}` | edit window |
| PATCH | `/admin/availability/{id}/deactivate` | close window (see §6) |

Responses use DTOs only. `DoctorAdminResponse` deliberately excludes the password
hash and login bookkeeping — the response type simply has no such field, so it
*cannot* leak.

---

## 3. Specialties: soft retirement, no deletes

Specialties are reference data. Existing appointments point at them, so an admin
can never physically delete one — deactivation (`is_active = false`) keeps history
intact while removing the specialty from booking discovery. Name uniqueness is
checked case-insensitively *before* the insert ("Cardiology" blocks "cardiology"),
producing a clean 409 instead of a database constraint error (500).

---

## 4. Doctors: one transaction, three writes

Creating a doctor means creating:

1. the `users` row (password stored only as a BCrypt hash),
2. the `DOCTOR` role assignment,
3. the `doctors` profile row.

`DoctorAdminService.create` wraps all three in **one transaction**: a duplicate
license detected after the user row was written rolls the user back too, so the
database can never hold a login account without a doctor profile. Tests prove
this directly (`failedDoctorCreationLeavesNoUserAndNoDoctorRows`).

Security invariants enforced on the server:

- the created account always gets the DOCTOR role — this API cannot mint an ADMIN;
- duplicate email → 409, duplicate license → 409;
- password never echoed in a response;
- profile edits (PUT) do not touch credentials; account management is a separate,
  future concern.

---

## 5. Availability: windows, not bookings

Availability windows define *when a doctor could* take appointments — who actually
booked what stays in `appointments` (conflict logic lives in `AppointmentService`,
deliberately not duplicated here). Validation mirrors the schema: weekday 0–6,
`start_time < end_time`, `effective_from <= effective_until`, and the exact same
window cannot exist twice for one doctor (409).

---

## 6. "Deactivating" availability without a migration

The `doctor_availability` table has **no `is_active` column** — and Step 19 adds
no migration. The schema-native equivalent of deactivation is *closing* a window:
`effective_until` is clamped to yesterday, so the row and its history remain but
stop applying to future dates. A window that starts entirely in the future has
never applied to anything, so closing it deletes the row (clamping would violate
the schema's `effective_from <= effective_until` CHECK constraint). Booking-time
validation automatically rejects closed windows because it checks effective dates
against the same table.

---

## 7. Backend authorization

`SecurityConfig` adds one matcher:

```
.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
```

Verified behavior (real JWTs in `AdminManagementTest`):

| Caller | Result |
|---|---|
| anonymous | **401** (authentication entry point) |
| PATIENT | **403** (`MedbookAccessDeniedHandler`) |
| DOCTOR | **403** |
| ADMIN | allowed |

Spring Security evaluates the roles carried inside the JWT — the frontend cannot
opt itself in.

---

## 8. Frontend architecture

- **API layer** (`api/adminApi.ts`): one function per endpoint, shapes mirroring
  the backend DTOs, using the centralized Axios client (JWT attached automatically).
- **React Query hooks** (`hooks/useAdmin.ts`): `useAdminSpecialties`,
  `useCreateSpecialty`, `useUpdateSpecialty`, `useDeactivateSpecialty`, and the
  doctor/availability equivalents. Query keys are namespaced
  (`['admin', 'specialties']`, …) and mutations invalidate both the admin list
  **and** the public discovery caches (`['specialties']`, `['doctors']`,
  `['doctorAvailability']`) so the booking page reflects admin changes without a
  reload. No server state lives in AuthContext.
- **Routes** (`App.tsx`): `/admin`, `/admin/specialties`, `/admin/doctors`,
  `/admin/availability` — each wrapped in `AdminRoute`.
- **Role handling** (`AdminRoute`): reads `user.roles` from AuthContext (populated
  by the backend at login) and shows a friendly "Access denied" card to non-admins.
  **This is UX, not security** — a non-admin who bypasses the UI and calls the APIs
  directly still gets 403 from the backend. The dashboard shows the "Admin Area"
  card only to admins.
- **Forms**: React Hook Form + Zod on every form (specialty name, doctor
  credentials/profile, availability windows with the start-before-end and
  date-range rules). Errors from the backend (400/401/403/404/409/500) are shown
  through the existing `toApiError` normalization — human-readable, never raw.

---

## 9. UI consistency

The admin area reuses the existing MedBook language rather than inventing a second
one: the same `Button`/`Input`/`Select`/`Card` components, the same Tailwind v4
setup and gray-50 page background, the same nav bar pattern as the Dashboard, the
same status-badge style for Active/Inactive/Open/Closed, Lucide icons, and the
same loading/empty/error state patterns. The only new shared components
(`PageHeader`, `StatusBadge`, `EmptyState`, `ErrorBox`, `Modal`) were extracted
because all admin pages use them — not as a premature design system.

The availability form maps friendly day names
(Sunday…Saturday) to the backend's numeric 0–6 convention; the admin never types
a raw number.

---

## 10. Tests

**Backend** (`AdminManagementTest`, 26 tests over real MySQL with real JWTs):
specialty create/duplicate/update/deactivate + discovery reflection; doctor
creation asserting the DOCTOR role, hashed password and no ADMIN role; duplicate
email/license with rollback verification; profile update; doctor deactivation
reflected in discovery while existing appointments stay intact; availability
create/update/close + 400/409 paths; the full anonymous/patient/doctor rejection
matrix; and the no-residue transaction tests.

**Frontend** (`AdminPages.test.tsx`, 18 tests): dashboard cards + links;
specialties list with active/inactive, empty state, create, duplicate-name 409
shown in the modal, validation blocking, deactivation; doctors list, empty state,
creation (asserting every field reaches the API), backend 409 surfacing, client
validation; availability doctor picker, window list, friendly-day creation,
invalid-time blocking, closing.

**E2E verification** (performed before release of this step): a real admin account
logs in, creates a specialty, a doctor (role verified in the database), an
availability window, then a real patient books with that doctor through the normal
discovery flow — and a patient token gets 403 on the admin APIs. Temporary rows
are cleaned afterwards and the database returns to 10 tables with Flyway 1/1.

---

## 11. Scope boundaries

Deliberately **not** part of Step 19: patient CRUD, admin appointment
cancellation/listing, payments, notifications, refresh tokens, new database
tables, or a separate admin design system.
