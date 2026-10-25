# MedBook — Complete Application Workflow & Gap Analysis

*Audit-only document. No application code was changed to produce it. Every claim
below was verified by reading the actual backend and frontend source in this
repository — nothing is assumed from a table name, route name, or a doc.*

---

## 0. Update — Role Bootstrap & Testable Role Flows (IMPLEMENTED)

> **Added after the audit was acted on.** Sections 1–16 below are the original audit,
> kept for the before/after record. Where they describe the role-accessibility problem
> they are now historical; the changes below are implemented, tested and documented.

### The role-accessibility problem — BEFORE vs AFTER

**BEFORE**

| Role | How you got an account |
|---|---|
| Patient | Self-register at `/register`. Worked. |
| Doctor | Only an admin could create one — but there was no first admin. |
| Admin | Manual SQL `INSERT INTO user_roles ...`. No in-app path. |

A fresh database had the three roles and nothing else: zero users, specialties,
doctors or availability. The entire Doctor and Admin product was implemented but
unreachable without editing MySQL by hand.

**AFTER**

| Role | How you get an account now |
|---|---|
| Patient | Unchanged — self-register at `/register` (Patient-only, by design). |
| Doctor | Admin → Doctors → New Doctor, **or** the dev seed (`doctor@medbook.test`). |
| Admin | A controlled startup **bootstrap** from environment variables, **or** the dev seed (`admin@medbook.test`). |

### What was added (no security was weakened)

- **First-admin bootstrap** (`backend/.../bootstrap/AdminBootstrapRunner`): on startup,
  when `medbook.bootstrap.admin-email` / `medbook.bootstrap.admin-password` are set and
  the **configured email** is not yet registered, creates that account with a BCrypt
  password and the `ADMIN` role only. Idempotent by email (an existing account is never
  duplicated or overwritten, even when other admins exist), no HTTP surface, never
  logs the password. In this repository the development/setup credentials are shipped
  in `application.properties` (the configured initial administrator); production
  overrides them via the same properties or the `MEDBOOK_BOOTSTRAP_ADMIN_*` environment
  variables, or disables the bootstrap by leaving email/password blank. It does **not**
  touch public registration.
- **Dev-only demo seed** (`DemoDataSeeder`, gated on `medbook.demo.seed=true` or the
  `dev` profile): creates the demo Admin, Doctor and Patient, two specialties, a full
  week of doctor availability, and a few realistic appointments (through the real
  `AppointmentService`, so history and notifications are consistent). Never runs in
  production.
- **Single source of demo credentials:** `docs/demo-accounts.md`.
- **Clearer login/register copy** (still one shared login, still Patient-only signup —
  no role selector anywhere).
- **Admin → Patients screen** for the previously unused `GET /api/v1/admin/patients`.
- **Participant names** (`patientName` / `doctorName`) added to the patient/doctor
  `AppointmentResponse`, replacing the old `Patient #12` / `Doctor #3` display.
- **Admin lands on the Admin dashboard** after login (`DashboardRouter` is now
  admin-aware, with the deterministic priority ADMIN > DOCTOR > PATIENT for accounts
  holding several roles); it previously showed the patient dashboard to admins.
- **Admin Management** (`/admin/admins` + `GET`/`POST /api/v1/admin/admins`): lists
  administrator accounts (name, email, status, roles — never any password) and lets an
  authenticated admin create additional administrators. The ADMIN role is assigned
  server-side; duplicate emails get 409; non-admins get 403; the password is stored
  BCrypt-hashed only. Administrator deactivation (with last-active-admin lockout
  protection) is a documented future feature — the API intentionally offers list +
  create only.

### How a new developer tests each role today

```text
1. Start MySQL, then:  cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev
2. Start the frontend: cd frontend && npm run dev
3. Open http://localhost:5173/login

   Patient → patient@medbook.test / Patient@123
   Doctor  → doctor@medbook.test  / Doctor@123
   Admin   → admin@medbook.test   / Admin@123
```

No SQL editing, no manual role grants. Full details and environment variables are in
`docs/demo-accounts.md`.

The security model is unchanged: public signup creates PATIENT accounts only; the
bootstrap is server-side and configuration-gated; the backend enforces every role.

---

## 1. Project Overview

MedBook is a clinic appointment management system. A patient picks a doctor, sees
that doctor's free time slots, and books a visit. Doctors see the appointments
booked with them and move each visit through a status lifecycle
(SCHEDULED → CONFIRMED → COMPLETED / NO-SHOW). Administrators set up the clinic:
medical specialties, doctor accounts and profiles, and each doctor's working hours.

- **Backend:** Spring Boot (Java 21), MySQL, Flyway. Token API (JWT Bearer).
- **Frontend:** React + Vite + TypeScript + Tailwind, MUI components, React Query.
- Everything runs in the browser; there is nothing to install for an end user.

The important thing to understand up front: **MedBook has one shared login page for
all roles.** What you can see and do after signing in depends on the roles stored
inside your account, not on which page you logged in from. There is no separate
"doctor login" or "admin login" URL.

---

## 2. User Roles

The system has exactly **three roles**. They are seeded by the Flyway migration
`V1__create_medbook_schema.sql` into the `roles` table:

| Role | Meaning |
|---|---|
| `PATIENT` | A person who books and manages their own appointments |
| `DOCTOR` | A healthcare provider who sees and closes their own appointments |
| `ADMIN` | A clinic administrator who manages specialties, doctors, hours, and clinic-wide appointments |

There is **no database ENUM** for roles; the three values are enforced at the
application level. A user's roles are stored in a many-to-many join table
(`user_roles`), so one account can technically hold more than one role (the demo
admin account holds both `ADMIN` and `PATIENT`).

The frontend reads the roles from the login response and stores them with the
token in `localStorage`. Guards then check for `ROLE_ADMIN` / `ROLE_DOCTOR`.

---

## 3. Authentication Flow

### Signup
Only one type of account can be created from the public signup page: a **Patient**.

- Frontend: `frontend/src/pages/RegisterPage.tsx` collects first name, last name,
  phone, date of birth, email, password. There is **no role selector** — the form
  has no field to choose a role.
- Backend: `POST /api/v1/auth/register` → `AuthService.register(...)`. The service
  hardcodes `DEFAULT_ROLE = "PATIENT"` and attaches exactly that role. The request
  DTO (`RegisterRequest`) has no `role` field at all, and Spring's Jackson setup
  ignores unknown JSON fields — so sending `{"role":"ADMIN"}` does nothing. (A
  backend test, `AuthControllerTest`, explicitly sends a forged `role`/`roles` body
  and asserts it is ignored.)

### Login
- One page for everyone: `frontend/src/pages/LoginPage.tsx` → `POST /api/v1/auth/login`.
- The backend verifies email + BCrypt password and returns a JWT plus a user summary
  that includes the `roles` list, e.g. `["ROLE_DOCTOR"]`.
- After login the frontend always navigates to `/dashboard`, and a small router
  inside `App.tsx` shows the **Doctor Dashboard** if the token has `ROLE_DOCTOR`,
  otherwise the **Patient Dashboard**.

### JWT / session
- The JWT is signed with HS256 (issuer `medbook`). It carries `sub` (user id),
  `email`, and `roles` (`ROLE_*` strings). It is base64-encoded, **not encrypted**.
- The token is stored in `localStorage` by `AuthContext` and attached to every
  request as `Authorization: Bearer ...`.
- Lifetime defaults to 60 minutes. There is no refresh token and no server-side
  session.
- If the backend rejects a token (401), an Axios interceptor clears the stored auth
  and redirects to `/login`.

### Role detection
- The backend turns the JWT `roles` claim directly into Spring Security authorities
  (`SecurityConfig.jwtAuthenticationConverter`), so `hasRole("ADMIN")` works.
- The frontend checks `user.roles.includes('ROLE_ADMIN')` / `'ROLE_DOCTOR'`.

### Authorization rules (backend, the real boundary)
From `backend/src/main/java/com/medbook/security/SecurityConfig.java`:

| Path | Rule |
|---|---|
| `/api/v1/auth/**` | Public (register / login) |
| `/api/v1/hello`, `/actuator/health`, `/actuator/info` | Public |
| `/api/v1/appointments/**` | Any of `ADMIN`, `DOCTOR`, `PATIENT` |
| `/api/v1/admin/**` | `ADMIN` only |
| everything else | Any authenticated caller |

Fine-grained rules (a patient may only read their *own* appointment, a doctor may
only confirm appointments *assigned to them*) are enforced in the service layer.

### Logout
- Client-side only: `AuthContext.logout()` clears the token from `localStorage`.
  Because tokens are stateless, the old token technically remains valid until it
  expires — there is no revocation list.

### Protected routes
- `ProtectedRoute` → requires any signed-in user.
- `DoctorRoute` → requires `ROLE_DOCTOR`; otherwise shows an "Access denied" card.
- `AdminRoute` → requires `ROLE_ADMIN`; otherwise shows an "Access denied" card.

These frontend guards are **UX only**. The real enforcement is the backend role
check — a patient who somehow reached an admin page would get `403` from every
admin API call anyway.

---

## 4. Patient Workflow

Fully implemented and reachable.

```
Register (/register)  ──►  auto signed in, lands on /dashboard
        │
        ▼
Login (/login)  ──►  Patient Dashboard (/dashboard)
        │
        ├── Book Appointment (/appointments/new)
        │       specialty → doctor → date → free time slot → confirm
        │
        ├── My Appointments (/appointments/me)
        │       list with All / Upcoming / Cancelled filters
        │       └── open one → Appointment Detail (/appointments/:id)
        │                       └── Cancel (reason required)
        │
        ├── My Profile (/profile)
        │       edit account details, patient profile, change password
        │
        └── Notifications (bell in the header)
```

Every step above exists and is wired to real backend endpoints. The patient
dashboard shows the next upcoming appointment pulled from `/appointments/me`
(no invented statistics).

---

## 5. Doctor Workflow

**Doctor functionality exists and is fully implemented** — it is simply not
reachable until a doctor account exists, which only an administrator can create
(see §7). Once a doctor signs in:

```
Admin creates doctor account  ──►  Doctor logs in at /login (same page)
        │
        ▼
Doctor Dashboard (/dashboard, or directly /doctor)
        ├── Today's appointments
        └── Upcoming appointments
        │
        ├── My Appointments (/doctor/appointments)
        │       all appointments assigned to this doctor
        │
        ├── Open an appointment (/appointments/:id)
        │       SCHEDULED → Confirm
        │       CONFIRMED → Mark Completed  or  Mark No-Show
        │
        └── My Profile (/profile)
```

Verified against the actual code:

| Doctor feature | Status |
|---|---|
| Doctor login (shared login page) | ✅ Implemented |
| Doctor dashboard (today + upcoming) | ✅ `DoctorDashboardPage.tsx` |
| List of own appointments | ✅ `DoctorAppointmentsPage.tsx` + `GET /appointments/me` |
| Confirm appointment | ✅ `POST /appointments/{id}/confirm` |
| Mark completed | ✅ `POST /appointments/{id}/complete` |
| Mark no-show | ✅ `POST /appointments/{id}/no-show` |
| View patient details | ⚠️ Partial — only `Patient #<id>` is shown; **no patient name** |
| Reschedule appointment | ❌ Not implemented |
| Medical records / consultation notes | ❌ Not implemented |
| Doctor manages own availability | ❌ Not implemented — availability is admin-managed |

So the answer to "is doctor functionality actually there?" is **yes, the backend
and frontend both exist** — the gap is that **there is no in-app way to obtain a
doctor account unless you are already an admin.**

---

## 6. Admin Workflow

**Admin functionality exists and is fully implemented** — but there is no
in-app way to create the *first* administrator (see §7). Once an admin signs in:

```
Admin logs in at /login (same page)
        │
        ▼
Admin Overview (/admin)          — live appointment counts by status
        ├── Appointments (/admin/appointments)
        │       clinic-wide list + filters (date, status, doctor, patient)
        │       └── detail (/admin/appointments/:id) — status history + cancel
        ├── Specialties (/admin/specialties)   — create / edit / deactivate
        ├── Doctors (/admin/doctors)
        │       └── "New Doctor" — creates the doctor's LOGIN + DOCTOR role + profile
        ├── Availability (/admin/availability) — weekly working hours per doctor
        └── My Profile (/profile)
```

Verified against the actual code:

| Admin feature | Status |
|---|---|
| Admin login (shared login page) | ✅ Implemented |
| Admin dashboard / overview | ✅ `AdminDashboardPage.tsx` + `GET /admin/appointments/overview` |
| Appointment management (list + filters) | ✅ `AdminAppointmentsPage.tsx` |
| Appointment detail + audit history | ✅ `AdminAppointmentDetailPage.tsx` |
| Support cancellation of appointments | ✅ `POST /admin/appointments/{id}/cancel` |
| Book on behalf of a patient | ✅ via `POST /appointments/admin` in the appointments UI |
| Specialty management (CRUD + deactivate) | ✅ `SpecialtiesAdminPage.tsx` |
| Doctor management (create/update/deactivate) | ✅ `DoctorsAdminPage.tsx` |
| Doctor availability management | ✅ `AvailabilityAdminPage.tsx` |
| Patient management (list) | ⚠️ Partial — backend `GET /admin/patients` exists; **no dedicated patient screen** in the admin nav |
| Patient management (edit / deactivate / delete) | ❌ Not implemented |
| Create additional admin accounts | ❌ Not implemented |
| Reports / analytics / system settings | ❌ Not implemented |

So the answer to "is admin functionality actually there?" is **yes** — again the
gap is that **you cannot become an admin from inside the app.**

---

## 7. Role Creation & Account Provisioning

This is the heart of the confusion. Verified mechanism for each role:

### Patient — self-registration
- `POST /api/v1/auth/register`, open to the public.
- The service always assigns `PATIENT`. Immediately after registering, the user is
  signed in and redirected to `/dashboard`.
- **Why signup only shows Patient:** because that is the only thing this endpoint
  is allowed to create, by design. The code comment in `AuthService` states it:
  "privilege escalation through the public endpoint is structurally impossible."

### Doctor — created by an administrator
- Endpoint: `POST /api/v1/admin/doctors` (ADMIN only), backed by
  `DoctorAdminService.create(...)`.
- In **one atomic transaction** it creates the `users` row (BCrypt password), assigns
  the `DOCTOR` role, and creates the `doctors` profile.
- Frontend: **Admin → Doctors → "New Doctor"** modal
  (`frontend/src/pages/admin/DoctorsAdminPage.tsx`). It collects the doctor's email
  and password (their future login) plus professional details.
- **There is no doctor self-registration and no invitation/approval system.**

### Admin — now bootstrapped from configuration (originally: no in-app creation at all)
- **Now:** a startup bootstrap creates the first admin from
  `MEDBOOK_BOOTSTRAP_ADMIN_EMAIL` / `MEDBOOK_BOOTSTRAP_ADMIN_PASSWORD` when none exists,
  and the dev seed can create `admin@medbook.test`. See §0 and `docs/demo-accounts.md`.
- There is still intentionally **no public endpoint** to create or promote an admin:
  only registration (PATIENT), admin doctor creation (DOCTOR) and the server-side
  bootstrap assign roles.
- `V1` seeded the three role rows only — no users, specialties, or doctors.
- **Historical:** the old documented method was a **manual SQL grant** (from
  `docs/demo-checklist.md`): register a normal account in the UI, then run:
  ```sql
  INSERT INTO user_roles (user_id, role_id)
  SELECT (SELECT id FROM users WHERE email='admin@demo.test'),
         id FROM roles WHERE name='ADMIN';
  ```
  Then log in again and the admin area appears. This is no longer required.

### The bootstrap problem (chicken and egg)
To create a doctor you must be an admin. To become an admin you must edit the
database by hand. So a **fresh install has no admin, therefore no way to create any
doctor, therefore no doctor login is reachable** without manual SQL. This is a real
gap, not a bug in the code — the code is working as designed, but the design has no
self-service bootstrap.

---

## 8. Frontend Route Map

From `frontend/src/App.tsx`.

| Route | Guard / Role | Page | Status |
|---|---|---|---|
| `/` | Public | HomePage | ✅ |
| `/login` | Public | LoginPage (all roles) | ✅ |
| `/register` | Public | RegisterPage (Patient only) | ✅ |
| `/dashboard` | Signed in | `DashboardRouter`: Admin dashboard if `ROLE_ADMIN`, Doctor dashboard if `ROLE_DOCTOR`, else Patient dashboard | ✅ |
| `/appointments/me` | Signed in | MyAppointmentsPage (patient & doctor; admin gets 403 from API) | ✅ |
| `/appointments/new` | Signed in | BookAppointmentPage (patient self-booking) | ✅ |
| `/appointments/:id` | Signed in | AppointmentDetailPage (ownership enforced server-side) | ✅ |
| `/profile` | Signed in | ProfilePage | ✅ |
| `/doctor` | `ROLE_DOCTOR` | DoctorDashboardPage | ✅ |
| `/doctor/appointments` | `ROLE_DOCTOR` | DoctorAppointmentsPage | ✅ |
| `/admin` | `ROLE_ADMIN` | AdminDashboardPage | ✅ |
| `/admin/appointments` | `ROLE_ADMIN` | AdminAppointmentsPage | ✅ |
| `/admin/appointments/:id` | `ROLE_ADMIN` | AdminAppointmentDetailPage | ✅ |
| `/admin/specialties` | `ROLE_ADMIN` | SpecialtiesAdminPage | ✅ |
| `/admin/doctors` | `ROLE_ADMIN` | DoctorsAdminPage | ✅ |
| `/admin/patients` | `ROLE_ADMIN` | AdminPatientsPage (read-only list + search) | ✅ |
| `/admin/availability` | `ROLE_ADMIN` | AvailabilityAdminPage | ✅ |
| `*` | — | redirect to `/` | ✅ |

There is **no route at all** for admin account creation or user/role management.

---

## 9. Backend API Map

Verified against the controller source.

| Endpoint | Method | Role | Purpose | Frontend connected? |
|---|---|---|---|---|
| `/api/v1/auth/register` | POST | Public | Patient self-registration (PATIENT only) | ✅ RegisterPage |
| `/api/v1/auth/login` | POST | Public | Sign in, returns JWT | ✅ LoginPage |
| `/api/v1/appointments/me` | GET | PATIENT, DOCTOR | Caller's own appointments (admin → 403) | ✅ Dashboard, MyAppointments, Doctor pages |
| `/api/v1/appointments` | POST | PATIENT | Book for self | ✅ BookAppointmentPage |
| `/api/v1/appointments/admin` | POST | ADMIN | Book on behalf of a patient | ✅ AdminAppointmentsPage |
| `/api/v1/appointments/{id}` | GET | All (ownership) | One appointment | ✅ AppointmentDetailPage |
| `/api/v1/appointments/{id}/cancel` | POST | PATIENT, DOCTOR | Cancel own/assigned | ✅ AppointmentDetailPage |
| `/api/v1/appointments/{id}/confirm` | POST | DOCTOR | Confirm | ✅ AppointmentDetailPage |
| `/api/v1/appointments/{id}/complete` | POST | DOCTOR | Complete | ✅ AppointmentDetailPage |
| `/api/v1/appointments/{id}/no-show` | POST | DOCTOR | Mark no-show | ✅ AppointmentDetailPage |
| `/api/v1/doctors` | GET | Auth | Doctor discovery | ✅ BookAppointmentPage |
| `/api/v1/doctors/{id}/availability` | GET | Auth | Doctor weekly hours / slots | ✅ BookAppointmentPage |
| `/api/v1/specialties` | GET | Auth | Active specialties | ✅ BookAppointmentPage |
| `/api/v1/me/profile` | GET | Auth | Own profile (role-aware) | ✅ ProfilePage |
| `/api/v1/me/account` | PUT | Auth | Update own name/phone | ✅ ProfilePage |
| `/api/v1/me/patient-profile` | PUT | PATIENT | Update patient profile | ✅ ProfilePage |
| `/api/v1/me/doctor-profile` | PUT | DOCTOR | Update doctor bio/picture | ✅ ProfilePage |
| `/api/v1/me/change-password` | POST | Auth | Change password | ✅ ProfilePage |
| `/api/v1/notifications` | GET | Auth | Own notifications | ✅ NotificationBell |
| `/api/v1/notifications/unread-count` | GET | Auth | Badge count | ✅ NotificationBell |
| `/api/v1/notifications/{id}/read` | PATCH | Auth | Mark one read | ✅ |
| `/api/v1/notifications/read-all` | POST | Auth | Mark all read | ✅ |
| `/api/v1/admin/patients` | GET | ADMIN | List all patients | ✅ AdminPatientsPage |
| `/api/v1/admin/specialties` | GET/POST | ADMIN | List / create | ✅ |
| `/api/v1/admin/specialties/{id}` | PUT | ADMIN | Update | ✅ |
| `/api/v1/admin/specialties/{id}/deactivate` | PATCH | ADMIN | Deactivate | ✅ |
| `/api/v1/admin/doctors` | GET/POST | ADMIN | List / **create doctor + login** | ✅ |
| `/api/v1/admin/doctors/{id}` | GET/PUT | ADMIN | Read / update | ✅ |
| `/api/v1/admin/doctors/{id}/deactivate` | PATCH | ADMIN | Deactivate | ✅ |
| `/api/v1/admin/doctors/{doctorId}/availability` | GET/POST | ADMIN | List / add hours | ✅ |
| `/api/v1/admin/availability/{id}` | PUT | ADMIN | Update window | ✅ |
| `/api/v1/admin/availability/{id}/deactivate` | PATCH | ADMIN | Close window | ✅ |
| `/api/v1/admin/appointments` | GET | ADMIN | Clinic-wide list + filters | ✅ |
| `/api/v1/admin/appointments/overview` | GET | ADMIN | Dashboard counts | ✅ |
| `/api/v1/admin/appointments/{id}` | GET | ADMIN | Detail + history | ✅ |
| `/api/v1/admin/appointments/{id}/cancel` | POST | ADMIN | Support cancellation | ✅ |
| `/api/v1/hello` | GET | Public | Smoke-test | — |
| `/actuator/health`, `/actuator/info` | GET | Public | Monitoring | — |

**No endpoint exists** for creating admins, changing a user's roles, or managing
users other than doctors.

---

## 10. Database Role Structure

- `roles` — three rows only: `ADMIN`, `DOCTOR`, `PATIENT`. Seeded by Flyway `V1`.
- `users` — one row per person: email, BCrypt `password_hash`, name, phone,
  `is_active`, `is_locked`, `failed_login_attempts`, `last_login_at`.
- `user_roles` — join table linking `users` ↔ `roles` (many-to-many, so an account
  can hold more than one role).
- Role-specific profiles hang off `users`: `patients`, `doctors`.

Only the `roles` table is populated by migration. There is **no migration that
inserts users, specialties, doctors, or appointments** — the app starts empty apart
from roles.

---

## 11. Backend vs Frontend Feature Matrix

| Feature | Backend | API | Frontend UI | Reachable from UI | Status |
|---|---|---|---|---|---|
| Patient signup | ✅ | ✅ | ✅ | ✅ | Complete |
| Patient login | ✅ | ✅ | ✅ | ✅ | Complete |
| Patient booking | ✅ | ✅ | ✅ | ✅ | Complete |
| Patient cancel own | ✅ | ✅ | ✅ | ✅ | Complete |
| Patient profile | ✅ | ✅ | ✅ | ✅ | Complete |
| Doctor login | ✅ | ✅ | ✅ | ⚠️ needs a doctor account | Complete (account-gated) |
| Doctor dashboard | ✅ | ✅ | ✅ | ⚠️ needs a doctor account | Complete (account-gated) |
| Doctor confirm/complete/no-show | ✅ | ✅ | ✅ | ⚠️ needs a doctor account | Complete (account-gated) |
| Doctor sees patient *details* | ✅ name | ✅ | ✅ (name) | ✅ | Complete (names only) |
| Doctor reschedule | ❌ | ❌ | ❌ | ❌ | Missing |
| Doctor manages own availability | ❌ | ❌ | ❌ | ❌ | Missing (admin-managed) |
| Admin login | ✅ | ✅ | ✅ | ✅ bootstrap / dev seed | Complete |
| Admin dashboard | ✅ | ✅ | ✅ | ✅ | Complete |
| Admin manages specialties | ✅ | ✅ | ✅ | ✅ | Complete |
| Admin creates doctors | ✅ | ✅ | ✅ | ✅ | Complete |
| Admin manages availability | ✅ | ✅ | ✅ | ✅ | Complete |
| Admin appointments (list/cancel) | ✅ | ✅ | ✅ | ✅ | Complete |
| Admin list patients | ✅ | ✅ | ✅ | ✅ | Complete (read-only) |
| Admin manage patients | ❌ | ❌ | ❌ | ❌ | Missing (intentional — backend has no such endpoints) |
| Admin lists admins | ✅ | ✅ | ✅ | ✅ | Complete (no password material) |
| Admin creates admins | ✅ | ✅ | ✅ | ✅ | Complete (server-side ADMIN role, BCrypt, 409 dup email) |
| Admin deactivates admins | ❌ | ❌ | ❌ | ❌ | Missing (documented future feature; needs last-active-admin lockout protection) |
| Reports / analytics / settings | ❌ | ❌ | ❌ | ❌ | Missing |

---

## 12. Current Gaps (original audit)

> The two **Critical** gaps and the two role-accessibility **High** gaps below were
> **fixed** in §0 (admin bootstrap, dev seed, login/register copy). The status column
> under each is the audit-time state.

### Critical

**Gap:** No in-app way to create the first Admin (bootstrap).
**Where:** Backend — no endpoint grants/creates the ADMIN role; frontend — no route or page.
**Current state:** The only way is a manual `INSERT INTO user_roles ...` in MySQL.
**Expected state:** A controlled, non-public way to provision the first admin
(env-configured bootstrap account, or a CLI/migration guarded to dev).
**Impact:** On a fresh install nobody can reach the admin area, and therefore nobody
can create a doctor either — the entire doctor and admin experience is unreachable
without database surgery.
**Suggested solution:** Add an application bootstrap that creates a configured admin
account on startup when one does not exist (credentials from environment variables),
never from public signup.

**Gap:** No seed/demo data for a fresh environment.
**Where:** `db/migration/` contains only `V1` (schema + roles) and `V2` (notifications).
**Current state:** Fresh database has roles but zero users, specialties, doctors, or
availability. The `@medbook.test` accounts referenced in docs/tests are not created
by any committed script.
**Expected state:** An optional dev-profile seed (specialty + a doctor + availability +
a demo admin) so the app is usable immediately after setup.
**Impact:** Developers must hand-build every fixture before anything can be tested.
**Suggested solution:** A dev/test-only seed migration or profile-guarded seeder.

### High

**Gap:** Doctor/Admin accounts are not discoverable or provisionable by a new user.
**Where:** UX — registration is Patient-only; no hint anywhere that other roles exist.
**Current state:** A new user sees only "Create your account" and concludes MedBook is
patient-only (this is exactly the reported confusion).
**Expected state:** Clear messaging that doctor accounts are created by clinic admins,
and how the team is meant to obtain an admin for testing.
**Impact:** The whole doctor/admin half of the product appears to not exist.
**Suggested solution:** Documented dev credentials + a short note on the login page;
no public role selector (see §13).

**Gap:** Registration/login is not role-aware at all.
**Where:** `LoginPage.tsx`, `RegisterPage.tsx`.
**Current state:** One generic card; a doctor signing in sees the same copy as a
patient; there is no mention that doctors log in here.
**Expected state:** Copy that acknowledges all roles ("Doctors and admins sign in
here too") without inventing separate portals.
**Impact:** Users cannot tell how a doctor or admin is expected to sign in.
**Suggested solution:** Add explanatory copy only.

**Gap:** No admin patient management.
**Where:** Backend has `GET /admin/patients`; no UI uses it; no edit/deactivate.
**Current state:** Backend read-only list exists; the admin nav has no Patients screen.
**Expected state:** Either a Patients screen, or documentation that admin patient
management is intentionally out of scope.
**Impact:** Admin cannot view/manage patients despite the API existing.
**Suggested solution:** Add an admin Patients page, or document the limitation.

### Medium

**Gap:** Appointments show numeric IDs instead of names.
**Where:** `DashboardPage`, `DoctorDashboardPage`, appointment views.
**Current state:** Displays "Patient #12" / "Doctor #3"; `AppointmentResponse` carries
no participant names.
**Expected state:** Human-readable names.
**Impact:** Poor usability; hard to know who an appointment is with.
**Suggested solution:** Include participant display names in `AppointmentResponse`.

**Gap:** Password change does not invalidate existing tokens.
**Where:** `MeController.changePassword` / stateless JWT model.
**Current state:** Old tokens remain valid until they expire (~60 min).
**Impact:** A changed password does not immediately lock out other sessions.
**Suggested solution:** Token versioning / revocation, or document the limitation.

**Gap:** Notifications are in-app only and poll every 30 seconds.
**Where:** `NotificationBell.tsx`, `NotificationController`.
**Current state:** No email/SMS; badge updates via polling.
**Impact:** Users miss updates when the app is closed.
**Suggested solution:** Out of scope for now; document the limitation.

**Gap:** No forgot-password / reset flow.
**Where:** Auth subsystem.
**Current state:** Only change-password while signed in.
**Impact:** Users who forget a password cannot recover it.
**Suggested solution:** Email-based reset, or document as out of scope.

### Low

**Gap:** Docs describe an "Admin Area card" on the dashboard that does not exist.
**Where:** `docs/user-guide.md`, `docs/demo-checklist.md`.
**Current state:** `DashboardPage.tsx` is the patient dashboard and renders no admin
card; admins actually reach the admin area through the **sidebar**.
**Impact:** Anyone following the docs looks for a card that isn't there.
**Suggested solution:** Correct the docs (or add the card — but this is audit-only).

**Gap:** `docs/demo-checklist.md` says the schema has 10 tables; it has 11 after the
notifications migration.
**Where:** Documentation.
**Impact:** Minor confusion during verification.
**Suggested solution:** Update the count.

**Gap:** Security docs mention `changed_by_user_id` "left for a later step", but the
wiring is now implemented.
**Where:** `docs/security.md` / `CurrentUserProvider` Javadoc.
**Impact:** Stale documentation.
**Suggested solution:** Refresh the docs.

---

## 13. Frontend UX Gaps

- **No indication that Doctor/Admin roles exist.** Registration and login never
  mention other roles, so the product reads as patient-only.
- **No role-aware login experience.** One generic form for everyone (intentional for
  security, but it needs explanatory copy).
- **No way to test the Doctor UI** without first being an admin (see bootstrap gap).
- **No way to test the Admin UI** without a manual SQL grant.
- **Admin patient list has no screen** — the API exists but nothing calls it.
- **Appointment participant names missing** — numeric IDs shown to users.
- **Doctor cannot view/manage their own availability** — only admins can.
- **No empty/error-state gap for the admin patient area** because the screen does
  not exist.

Positives verified: loading states, empty states, and error states **do** exist on the
pages that are wired up (dashboards, appointment lists, admin tables), and route
guards correctly show "Access denied" for the wrong role.

---

## 14. Testing / Demo Accounts

**This is the central practical problem.** Verified state:

- There is **no committed seed file** that creates any user, doctor, or specialty.
- The accounts repeatedly referenced in docs/tests
  (`john@medbook.test`, `admin@medbook.test`, `sarah.wells@medbook.test`, …) are
  **not created by the repository** — they only exist in a developer's local MySQL
  instance. A fresh clone cannot reproduce them.

How a developer tests each role **now** (was audit-time state before the fix):

| Role | How to get an account |
|---|---|
| **Patient** | Register on `/register`, or the dev seed account `patient@medbook.test`. |
| **Doctor** | The dev seed account `doctor@medbook.test`, or Admin → Doctors → New Doctor. |
| **Admin** | The dev seed account `admin@medbook.test`, or the env bootstrap, or (still) the manual SQL grant. |

**No manual database editing is required any more.** Credentials and configuration live
in `docs/demo-accounts.md`. There is still no role selector and no development-only role
switching in the frontend, because neither is needed and both would be a security smell.

Note: the automated test suites create their own throwaway users in the test
database, so the backend (293 tests) and frontend (95 tests) pass regardless — the
gap is about a *human* trying to click through the roles.

---

## 15. Recommended Development Flow

> **Status: implemented.** The steps below are what §0 put in place; they are kept as
the rationale for the design.

To make all three roles testable **without weakening security**:

1. **Add an admin bootstrap.** On startup, ensure the configured administrator account
   exists (create it from `medbook.bootstrap.admin-*` properties / environment
   variables when its email is not yet registered). This is server-side, non-public,
   and removes the chicken-and-egg problem. It is *not* a signup role selector.
2. **Add an optional dev seed profile.** A `spring.profiles.active=dev` seeder that
   inserts a demo specialty, a demo doctor (with availability), and a demo admin, so a
   fresh environment is immediately usable. Never active in production.
3. **Keep public registration Patient-only.** This is correct and should not change.
   A frontend role dropdown would be misleading (the server ignores it) and dangerous
   if anyone later "fixed" the server to honor it.
4. **Add explanatory copy** to `/login` and `/register`: "Doctors and admins sign in
   here; doctor accounts are created by a clinic administrator."
5. **Document dev credentials** centrally (single source of truth), replacing the
   inconsistent `@demo.test` / `@medbook.test` references.
6. **Then** consider richer features: an admin Patients screen, doctor
   availability self-management, participant names in appointment responses.

---

## 16. Current Project State

```text
Patient Flow:  Complete and reachable. Register → book → manage → cancel → profile.

Doctor Flow:   Backend + frontend both fully implemented (dashboard, schedule,
               confirm/complete/no-show). REACHABLE via the dev seed doctor account
               or an admin-created doctor (bootstrap makes the first admin possible).

Admin Flow:    Backend + frontend both fully implemented (specialties, doctors,
               patients, admins, availability, clinic-wide appointments). REACHABLE via
               the configured initial administrator (bootstrap), an admin-created
               admin account, or the dev seed admin account.

Backend completeness:  High. ~45 endpoints across 8 controllers; auth, appointments,
               doctors, specialties, admin management (doctors, patients, admins),
               profile, notifications all implemented. Missing only: admin
               deactivation, doctor rescheduling, forgot-password.

Frontend completeness: High for what exists. 15 pages + role guards + full admin
               area, all wired to real APIs with loading/empty/error states.

Authentication completeness: Solid JWT model. Public signup is safely Patient-only;
               role assignment is server-side and cannot be forged. Missing: token
               revocation on password change, password reset.

Role management completeness: Strong for provisioning. Public signup mints PATIENT
               accounts; the startup bootstrap provisions the configured initial
               administrator; authenticated admins create DOCTOR and ADMIN accounts
               through the UI; roles are always assigned server-side. Missing: admin
               deactivation (with last-active-admin lockout protection).

Biggest gaps after the fix:
1. Admin deactivation is not implemented (documented future feature); when it is, the
   last-active-admin lockout protection must be enforced server-side.
2. Admin patient management is read-only (list + search); the backend exposes no
   edit/deactivate/delete for patients, so the UI deliberately offers none.
3. Still missing (unchanged, out of scope): token revocation on password change,
   password reset, doctor self-managed availability, doctor rescheduling.
```
