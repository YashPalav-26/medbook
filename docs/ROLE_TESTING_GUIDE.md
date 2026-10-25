# MedBook — Role Testing Guide

> **MedBook has one shared login page. There are no separate Doctor or Admin login URLs.**
> Every account — patient, doctor, or administrator — signs in at `/login` with an email
> and a password. What you land on and what you can do afterwards is decided by the
> roles stored inside your account, enforced by the backend on every request.

---

## TL;DR — the four account flows

```text
PATIENT          → self-registers at /register        → /login → Patient Dashboard
INITIAL ADMIN    → created by application bootstrap    → /login → Admin Dashboard
DOCTOR           → created by an Admin in the UI       → /login → Doctor Dashboard
ADDITIONAL ADMIN → created by an Admin in the UI       → /login → Admin Dashboard
```

All accounts use the same `/login` page. There is no role selector anywhere in the
public UI, and no `/admin/login`, `/doctor/login`, or `/patient/login` route exists.

---

## 1. The shared login page

- URL: `/login` (frontend route; served by `LoginPage.tsx`).
- Form fields: **Email** and **Password** only — no role selector, no role tabs.
- After login the app inspects the roles in the JWT response and routes
  deterministically with the priority **ADMIN > DOCTOR > PATIENT**
  (`DashboardRouter` in `frontend/src/App.tsx`). An account holding both
  `ROLE_ADMIN` and `ROLE_PATIENT` lands on the Admin Dashboard; no role is ever
  removed from the account.
- The page copy states that patients, doctors, and administrators all use this login.
- `/register` creates **patient accounts only**. The page says so explicitly:
  doctor and administrator accounts are created by the clinic. There is no public
  role selector, and no public doctor/admin registration exists anywhere.

---

## 2. Accounts and how they are provisioned

| Account | Provisioned by | Role(s) assigned |
|---|---|---|
| Any patient | Self-registration at `/register` | `PATIENT` only |
| Initial admin (Yash Palav, `yashpalav100@gmail.com`) | `AdminBootstrapRunner` at startup from `medbook.bootstrap.admin-*` in `application.properties` | `ADMIN` |
| Doctors | Admin → **Doctors → New Doctor** (`POST /api/v1/admin/doctors`) | `DOCTOR` |
| Additional admins | Admin → **Admins → Add Admin** (`POST /api/v1/admin/admins`) | `ADMIN` |
| Demo/dev accounts (`@medbook.test`) | `DemoDataSeeder` (dev profile only) | as seeded |

Account-provisioning rules enforced by the backend:

- Passwords are stored **BCrypt-hashed only** — never plaintext, never returned by
  any API, never logged.
- Roles are assigned **server-side**. No request body can choose a role; the
  endpoint fixes it (register → PATIENT, admin/doctors → DOCTOR, admin/admins → ADMIN).
- Duplicate emails are rejected with `409`.
- The bootstrap is idempotent by email: restarting the application never duplicates
  the initial admin, never overwrites its password, and never duplicates its role.
- There is **no public endpoint** to create a doctor or an admin
  (`POST /register-admin` does not exist; admin/doctor creation is under
  `/api/v1/admin/**`, which requires `ROLE_ADMIN`).

---

## 3. Manual test flows (each takes ~2 minutes)

Prerequisites: MySQL running, backend on `:8080`
(`cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev`), frontend on
`:5173` (`cd frontend && npm run dev`).

### Flow 1 — Initial admin (bootstrap → login)

1. Start the backend. On first startup (or whenever the configured email is absent)
   the log shows `Bootstrap administrator account ready for yashpalav100@gmail.com`.
2. Restart the backend once more. No duplicate is created (the log no longer reports
   a fresh creation; the account row count is unchanged).
3. Open `http://localhost:5173/login`, sign in with the configured admin email and
   password (see `application.properties` — they are not reproduced here).
4. **Expected:** you land on the Admin Dashboard (`/admin`), the sidebar shows the
   Administration section, and the header shows your name with the ADMIN label.

### Flow 2 — Admin creates a doctor → doctor logs in

1. Sign in as an admin → **Doctors** → **New Doctor**.
2. Fill in email, password (min 8 chars), names, phone, specialty, license,
   experience, fee → **Create Doctor**.
3. Sign out. Sign in at `/login` with the doctor's email and password.
4. **Expected:** you land on the Doctor Dashboard; the header shows the DOCTOR label;
   My Appointments shows the doctor's schedule.

### Flow 3 — Admin creates another admin → new admin logs in

1. Sign in as an admin → **Admins** (sidebar, Administration section) → **Add Admin**.
2. Fill in email, password (min 8 chars), names, phone → **Create Admin**.
3. The list refreshes showing the new account with the ADMIN role and Active status.
4. Sign out. Sign in at `/login` with the new admin's email and password.
5. **Expected:** you land on the Admin Dashboard. The new account appears in the
   **Admins** list for every administrator.

### Flow 4 — Patient registers → logs in

1. Open `/register` (no role selector), create a patient account.
2. Sign in at `/login`.
3. **Expected:** you land on the Patient Dashboard; you can book an appointment,
   manage appointments, and edit the profile. The account holds `ROLE_PATIENT` only.

### Negative checks (authorization is backend-enforced)

- Sign in as a patient or doctor and open `http://localhost:5173/admin` → the
  frontend shows the Access-Denied card; any direct API call to `/api/v1/admin/**`
  returns **403** (401 when anonymous).
- Signing in as a doctor/patient and calling `POST /api/v1/admin/doctors` or
  `POST /api/v1/admin/admins` returns **403** — only admins can provision accounts.

---

## 4. Automated tests covering the role workflow

Backend (`mvn test`, in `backend/`):

- `AdminBootstrapRunnerTest` — bootstrap policy: disabled when unconfigured, uses the
  configured credentials, runs even when other admins exist, failures never stop
  startup.
- `BootstrapAccountServiceTest` — BCrypt hashing, exactly-one-role assignment,
  idempotency by email, **existing admin never overwritten** (same id, original
  password still valid, second password ignored, no duplicate role).
- `AdminManagementTest` — admin create/list APIs, admin → login round trip, 409
  duplicate email, 400 validation, and the full authorization matrix (anonymous 401 /
  patient 403 / doctor 403 / admin allowed, for both doctor and admin creation).
- `AuthControllerTest` / `SecurityFilterTest` — login and JWT behavior for all roles.

Frontend (`npm test`, in `frontend/`):

- `App.dashboard.test.tsx` — dashboard selection: admin → Admin Dashboard,
  doctor → Doctor Dashboard, patient → Patient Dashboard.
- `AdminPages.test.tsx` — Admin Management page: list (no password material),
  create-admin payload contains no role field, duplicate-email error surfaced in the
  modal, client-side validation.
- Route-guard behavior (admin/doctor/patient) is covered by the dashboard and
  page-level tests.

---

## 5. Notes

- The configured initial admin's password lives in
  `backend/src/main/resources/application.properties` as the explicitly requested
  development/setup configuration. It is never printed in logs, never returned by an
  API, and stored in the database only as a BCrypt hash. Production overrides these
  properties (or the `MEDBOOK_BOOTSTRAP_ADMIN_*` environment variables); leaving
  email/password blank disables bootstrap provisioning.
- Administrator **deactivation** is a documented future feature. When implemented, it
  must enforce the last-active-admin lockout server-side (an operation that would
  deactivate the final active admin must be rejected). Until then the Admin
  Management screen offers list + add only.
