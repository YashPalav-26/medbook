# Appointment Authorization

How MedBook decides **who may see and cancel which appointment**. Written for someone new to the project: we start with the ideas, then show the code paths and the tests that prove the behavior.

## Authentication vs ownership authorization

Two different questions, asked at two different layers:

| Question | Layer | Answer source |
|---|---|---|
| "Who are you?" | Spring Security filter | The JWT in the `Authorization: Bearer` header |
| "May you touch **this** appointment?" | `AppointmentService` | The ownership rules in this document |

The security filter already rejects strangers: every `/api/v1/appointments/**` request needs a valid JWT carrying one of the `ADMIN` / `DOCTOR` / `PATIENT` roles. No token → **401** before any application code runs.

But a valid token only proves identity. Without ownership checks, *any* patient could read *any* appointment by guessing IDs — that was the documented Step 16 limitation, fixed in Step 17.

## Why frontend hiding is not authorization

Hiding the Cancel button in React changes what the UI *draws* — nothing else:

- HTTP requests can be crafted by hand (curl, Postman) with no browser at all.
- The bundle is public; roles stored in `localStorage` are trivially edited.
- Even a "hidden" route remains reachable by typing the URL.

So the React app may hide buttons for tidiness, but the **server decides**. Every read, list, and cancel passes through the service-layer rules below. A request that skips the UI and goes straight to the API gets exactly the same answer.

## Where identity comes from

`SecurityConfig`'s JWT filter validates the token and puts a `JwtAuthenticationToken` into the `SecurityContextHolder`. `CurrentUserProvider` reads it:

- `currentUserId()` — the JWT `sub` claim (the user's ID), or empty,
- `requireCurrentUserId()` — same, or throws when nobody is authenticated.

**A user ID sent from the browser (body, query, path) is never trusted for authorization.** The booking request still takes `patientId` because booking-for-a-family-member is a business input, not an identity claim — the caller's *rights* always come from the token.

The service resolves that ID against the `users` table (`requireCurrentUser()`), so a token whose account was deleted is rejected with a clear 403 instead of a confusing 500.

## The ownership rules

Implemented in `AppointmentService.canAccess(...)` / `canCancel(...)`, driven by the real role model (`roles` table: `ADMIN`, `DOCTOR`, `PATIENT`).

| Operation | PATIENT | DOCTOR | ADMIN |
|---|---|---|---|
| Read one appointment by ID | only their own | only ones assigned to them | any (support) |
| `GET /appointments/me` | their bookings | their assigned appointments | **403** (no listing yet) |
| Cancel | only their own | only ones assigned to them | **403** (policy decision pending) |

Notes:

- "Their own" is resolved through the profile tables: `Appointment → Patient → User` and `Appointment → Doctor → User`. The JWT identifies the *user*; ownership compares that user to the appointment's patient/doctor user.
- **ADMIN read** is the defined administrative rule: admins may look at any appointment (support use case) but can *change* nothing.
- **ADMIN cancel** is deliberately refused: silently cancelling a patient's clinic visit is a product decision that has not been made. When it is, `canCancel()` is the single place to change.
- Roles combine additively (a user with several roles gets the union of permissions).

## `GET /api/v1/appointments/me`

One endpoint, role-dependent meaning:

- PATIENT → appointments they booked,
- DOCTOR → appointments assigned to them,
- ADMIN → 403 (a global appointment feed is an admin dashboard feature that does not exist yet; failing loudly beats silently returning everything).

The endpoint takes **no user ID** — there is no `GET /appointments?userId=...` for callers to abuse. Listing uses fetch-join queries (`findAllByPatientUserId` / `findAllByDoctorUserId`) that traverse to the user ID so the JWT value can be used directly.

In React Router terms the frontend page lives at `/appointments/me`; the Spring route is `GET /api/v1/appointments/me`. The controller maps `/me` **before** `/{id}` so "me" is never parsed as an ID.

## Recording the caller: `changed_by_user_id`

`appointment_status_history.changed_by_user_id` used to be always NULL (authentication was not wired into the old flow). Now:

- **Cancellation over HTTP** records the authenticated caller in the CANCELLED history row.
- **Creation over HTTP** records the caller in the SCHEDULED row.
- **Context-free internal/test calls** (no security context at all) still write NULL — a NULL means "no caller legitimately existed", and the system never invents a user to fill the gap.

The Step 17 E2E run verified this with a real login: the CANCELLED history row carried the patient's actual user ID.

## 401 vs 403 vs 404

| Status | Meaning here | Example |
|---|---|---|
| **401** | We don't know who you are | No/expired/garbage token on any appointment endpoint |
| **403** | We know you; you may not do this | Patient A reading patient B's appointment; admin cancelling; admin calling `/me` |
| **404** | The row does not exist (for anyone) | `GET /appointments/999999` |

Rationale for keeping 403 rather than disguising ownership denials as 404:

- 404-hiding is a legit technique when row *existence* is itself secret. Here, IDs already appear in URLs the participant created; the project chose the honest, debuggable status. The service never leaks *which* appointments exist to a caller who cannot see them — the denial message is generic ("You do not have access to this appointment", no ID echoed).
- Unauthenticated callers never reach the ownership layer at all: the security fence answers 401 first.
- Ownership failures reuse Spring Security's `AccessDeniedException` (via `AppointmentOwnershipException`), translated by `GlobalExceptionHandler` into the standard `ErrorResponse` body — the same shape every other error uses, and never a 500.

## Server-side enforcement, end to end

`GET /appointments/{id}` flow:

1. Security filter validates the JWT, requires ADMIN/DOCTOR/PATIENT role → else 401/403.
2. Controller calls `appointmentService.getAppointment(id)` — no caller data from the request.
3. Service resolves the caller from the `SecurityContextHolder`, loads the appointment, applies `canAccess`.
4. Allowed → the stored appointment (200). Not allowed → 403. Missing → 404.

Cancellation is the same plus a pessimistic row lock, the status rules (can't cancel twice, can't cancel COMPLETED/NO_SHOW), and the history row with the real caller.

## The tests that prove it

- `AppointmentOwnershipTest` (service layer, real MySQL, security-context JWTs): patient A reads/cancels own ✓, patient B denied on A's (403, and nothing changed) ✓, doctor A sees assigned ✓, doctor B denied ✓, admin read-any ✓, admin cancel/me rejected ✓, `/me` listings never contain another patient's rows ✓, token for a deleted user rejected ✓, unauthenticated service calls rejected ✓, `changed_by_user_id` equals the real caller (or stays NULL when no caller exists) ✓.
- `AppointmentControllerTest` (HTTP layer): `/me` returns only the caller's appointments as JSON, empty list for a fresh patient, 403 for admin, 401 anonymous, plus the legacy contract tests (now role-aware).
- `SecurityFilterTest`: tokens without a business role → 403; a syntactically valid token with no matching user row → 403 from the ownership layer.
- Real end-to-end (manual, Step 17): patient A and patient B accounts in a real browser — B could not read or cancel A's appointment; A's cancellation wrote A's user ID into history. All temporary rows were removed afterwards.

## Limitations

- Ownership comparisons run inside the service call; there is no database-level row security.
- ADMIN listing/cancellation are unimplemented by design (see matrix above).
- `GET /appointments/{id}` uses exact-403 semantics (see the 401/403/404 rationale).
- Booking still accepts any existing `patientId` from the request; authorization-izing *booking* (e.g. forcing patients to book only for themselves) is a future policy step.
