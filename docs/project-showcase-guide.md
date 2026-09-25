# MedBook — Technical Project Documentation & Application Reference

*Personal project documentation for the MedBook clinic appointment system.*

---

## 1. Project Overview

MedBook is a full-stack clinic appointment management system built with React and Spring Boot.

- Patients register, browse specialties and doctors, view real availability, and book appointments.
- Doctors view and manage their assigned appointments (confirm, complete, no-show).
- Administrators manage specialties, doctors, and availability.
- JWT-based authentication protects the API.
- Appointment changes are recorded in an audit history.
- MySQL stores all application data.

**Problem it solves:** without a system, booking a doctor visit means phone calls and paper diaries. Nobody can see which slots are free, and nothing prevents two bookings for the same slot. MedBook makes real availability visible and lets the database guarantee one patient per slot.

## 2. System Objectives

- Make the booking process self-service and visible: patients see real availability.
- Guarantee data integrity: double-booking is impossible, not just unlikely.
- Keep a complete audit trail: every appointment status change records who made it.
- Enforce security on the server: identity comes from a signed token, ownership is checked per request.

## 3. Users and Roles

| Role | Can do (implemented ✅) | Not implemented yet (❌) |
|---|---|---|
| **PATIENT** (visitor) | Register, log in, browse homepage | — |
| **PATIENT** (signed in) | Dashboard, browse specialties & doctors, view availability, book for *themselves*, view own appointments, cancel own appointment (reason required) while SCHEDULED/CONFIRMED | Edit profile |
| **DOCTOR** | Dashboard (today/upcoming), see appointments assigned to them, Confirm a SCHEDULED visit, Complete or No-Show a CONFIRMED visit, cancel assigned appointments | Edit own profile, manage own availability |
| **ADMIN** | Manage specialties (create/edit/deactivate), manage doctors (account + profile in one transaction), manage availability, view all specialties/doctors | Cancel appointments (deliberate policy decision), dashboard statistics |

Roles are stored in the `roles` table and attached via `user_roles`. The JWT carries them, e.g. `["ROLE_PATIENT"]`.

## 4. Application Workflow (end to end)

1. Admin sets up the clinic: specialty → doctor → weekly availability.
2. Patient registers and logs in.
3. Patient browses by specialty, picks a doctor, picks a date, sees the available time windows.
4. Patient books a slot; the server validates and stores it.
5. Doctor sees the visit in their schedule and confirms it.
6. After the visit: completed (or no-show).
7. Either side can cancel while the appointment is SCHEDULED/CONFIRMED.
8. Every status change is written to the history table.

## 5. System Architecture

```
Browser (Chrome)
   ↓  HTTP + JSON + "Authorization: Bearer <JWT>"
React frontend (Vite, port 5173)
   ↓
Spring Boot REST API (port 8080)          ← @RestController classes
   ↓
Spring Security                           ← validates JWT, checks role
   ↓
Service layer (@Service)                  ← business rules, transactions
   ↓
JPA Repository (Spring Data interfaces)   ← Hibernate translates to SQL
   ↓
MySQL (database "medbook")
```

Each layer, one sentence:

- **React** — the screens the user sees; re-renders on data changes, no page reloads.
- **Axios** — the HTTP client carrying requests (with the JWT attached) to the backend.
- **Spring Boot** — receives requests, routes them to controllers, applies rules, returns JSON.
- **Spring Security** — checks *who you are* (JWT) and *what you're allowed to do* (role, ownership).
- **Service layer** — the rulebook: availability, conflicts, transitions, ownership.
- **JPA / Hibernate** — translates between Java objects and database rows.
- **MySQL** — permanent storage.

## 6. Technology Stack

| Technology | Where | Why |
|---|---|---|
| Java 21 | backend | Modern LTS language |
| Spring Boot 4.1.1 | backend (`pom.xml`) | Server framework, convention over configuration |
| Spring Web MVC | backend | REST endpoints (`@RestController`) |
| Spring Data JPA + Hibernate | backend | Database access without hand-written SQL for simple cases |
| Spring Security + OAuth2 Resource Server | backend | JWT validation, role checks, 401/403 handling |
| Jakarta Bean Validation | backend DTOs | Declarative input validation |
| Flyway | backend | Version-controlled schema |
| MySQL 8 | database | Relational model fits patients/doctors/appointments; transactions + constraints |
| Maven | backend | Build + test runner |
| React 19 | frontend | Component-based UI |
| TypeScript | frontend | Type safety before the code runs |
| Vite | frontend | Dev server + production bundler |
| Tailwind CSS v4 | frontend (`@tailwindcss/vite`) | Utility CSS; design tokens in `index.css` `@theme` |
| React Router | frontend | Navigation without reloads |
| Axios | frontend (`src/api/client.ts`) | HTTP client; auto-attaches JWT, normalizes errors |
| TanStack React Query | frontend | Server-state cache, loading/error states |
| React Hook Form + Zod | frontend | Forms with schema validation |
| Lucide | frontend | Icons |
| JUnit + Spring Test + MockMvc | backend tests | Integration tests against real MySQL with real JWTs |
| Vitest + Testing Library | frontend tests | Component tests |

## 7. Project Structure

```
medbook/
├── backend/
│   ├── pom.xml                          ← dependencies & build
│   └── src/main/java/com/medbook/
│       ├── controller/                  ← REST endpoints (thin, no rules)
│       ├── service/                     ← business rules + exceptions
│       ├── repository/                  ← Spring Data JPA interfaces
│       ├── entity/                      ← Java classes mirroring DB tables
│       ├── dto/                         ← request/response shapes (never entities)
│       ├── security/                    ← JWT, SecurityConfig, CurrentUserProvider
│       └── exception/                   ← GlobalExceptionHandler → clean JSON errors
│   └── src/main/resources/db/migration/
│       └── V1__create_medbook_schema.sql
│   └── src/test/java/com/medbook/       ← 230 backend tests (real MySQL)
├── frontend/
│   └── src/
│       ├── pages/                       ← one file per screen (+ admin/)
│       ├── components/ui/               ← design system: Button, Card, Modal, Toast…
│       ├── components/layout/           ← AppShell (sidebar + header, role-aware)
│       ├── hooks/                       ← React Query hooks
│       ├── api/                         ← Axios calls mirroring backend DTOs
│       └── types/                       ← TypeScript mirrors of backend DTOs
└── docs/
```

**Why the split:** controllers only translate HTTP ↔ Java; services hold the rules so they can be tested without HTTP; repositories only describe data access; DTOs keep internal details (e.g. password hashes) out of JSON responses.

## 8. Database Design

9 schema tables (+ Flyway's own history table = 10 live tables).

| Table | Stores | Why it exists |
|---|---|---|
| `users` | Login accounts: email, BCrypt **hash**, name, phone, active flag | Every person is a user first |
| `roles` | The three role names | Fixed lookup data |
| `user_roles` | Which user has which role | A user can hold several roles |
| `specialties` | Medical departments, active flag | Doctors belong to one; patients browse by it |
| `doctors` | Profile: license number, experience, bio, consultation fee, active flag, links to `users` + `specialty` | Professional profile separate from login account |
| `patients` | Profile (date of birth), links to `users` | Same separation for patients |
| `doctor_availability` | Weekly windows: day 0–6, start/end time, recurring flag, effective dates, active | The schedule booking validates against |
| `appointments` | Patient, doctor, date, start/end, status, fee **snapshot**, cancellation reason, timestamps | The core business object |
| `appointment_status_history` | Every status change: appointment, new status, changed_by_user_id, notes, timestamp | The audit trail (§15) |

**Relationships:**

- **One-to-one:** `users` ↔ `patients`, `users` ↔ `doctors`.
- **One-to-many:** one specialty → many doctors; one doctor → many appointments / availability windows; one appointment → many history rows.
- **Many-to-one:** many appointments → one patient.
- **Many-to-many:** `users` ↔ `roles` via `user_roles`.

## 9. Database Migration with Flyway

**Concept:** Flyway applies numbered SQL files to the database once each, in order, and records what it applied in `flyway_schema_history`.

**How it works in MedBook:** `V1__create_medbook_schema.sql` creates all 9 tables, keys, and CHECK constraints (availability day 0–6; `start_time < end_time`; etc.).

**Why it matters:** the schema is code — the exact database can be rebuilt from scratch by starting the application. No manual table editing, no drift between machines.

`spring.jpa.hibernate.ddl-auto=validate` — Hibernate does **not** create or alter tables. It only verifies that every entity matches the real schema at startup and refuses to start on a mismatch.

Key concept: **"Flyway owns the schema; Hibernate only verifies the Java matches it."**

## 10. Authentication and Security

**Registration / login flow:**

```
Register → POST /api/v1/auth/register → BCrypt hash → users table
           (PATIENT role + patients profile row created too)
Login    → POST /api/v1/auth/login → hash verified → JWT signed (60-min expiry)
Browser  → stores JWT (localStorage) + user/roles (AuthContext)
Requests → Axios adds "Authorization: Bearer <token>"
Server   → Security validates signature + expiry → roles from token → route rules
```

**JWT:** a signed token the server issues at login. The client presents it on every request; the server verifies the signature and expiry without storing sessions (stateless).

- **Authentication** = who are you? (valid token?)
- **Authorization** = what are you allowed to do? (right role? right owner?)

**Route rules (`SecurityConfig`):**

| Path | Access |
|---|---|
| `/api/v1/auth/**`, `/api/v1/hello`, `/actuator/health` | public |
| `/api/v1/appointments/**` | any signed-in role |
| `/api/v1/admin/**` | ADMIN only |

**BCrypt:** passwords are never stored in plain text — only a slow, salted one-way hash.

**Failure behavior:** no/invalid token → 401; wrong role → 403; wrong owner → 403 (checked in the service, §12); unknown ID → 404.

## 11. Appointment Booking Workflow

Actual server-side sequence (`AppointmentService`):

```
Patient selects specialty → doctor → date → time (from availability API)
→ POST /api/v1/appointments   (body has NO patientId)
→ patient resolved FROM THE JWT (never from the form)
→ validations: doctor exists & active, patient exists & active
→ availability window check (weekday + time inside window + effective dates)
→ row lock (SELECT … FOR UPDATE)
→ conflict re-check: overlapping appointment for that doctor/date/time → 409
→ appointment saved with the doctor's CURRENT FEE SNAPSHOT
→ history row: SCHEDULED, "Appointment created", changed_by = patient
→ 201 with appointment JSON
```

**Row locking (`SELECT … FOR UPDATE`):** two simultaneous bookings both read "slot free" without a lock. The pessimistic lock makes the second transaction wait; when it proceeds it re-reads the committed state and fails with 409. Double-booking becomes impossible. An automated test runs two real concurrent requests to prove it.

**Fee snapshot:** the appointment stores the fee at booking time, so later fee changes don't rewrite past records.

## 12. Appointment Ownership and Authorization

**Rules (enforced in the service, server-side):**

- A patient can view/cancel only appointments where `appointment.patient` is *them* (from the JWT).
- A doctor can view/confirm/complete/no-show/cancel only appointments assigned to *them*.
- Admin deliberately has no appointment-cancellation rights.
- Ownership is checked **before** status — a wrong doctor gets 403 regardless of the appointment's state, so errors don't leak information.

**Technical note:** hiding buttons in React is UX, not security. Any HTTP client can call any endpoint; the server re-checks role + ownership on every request. Patient B requesting Patient A's appointment gets 403, and B's appointment list never contains A's records because the list is built from the token.

## 13. Doctor Discovery and Availability

- `GET /api/v1/specialties` — active specialties.
- `GET /api/v1/doctors?specialtyId=…` — active doctors (name, specialty, experience, bio, fee).
- `GET /api/v1/doctors/{id}/availability?date=…` — that doctor's windows, optionally for one date.

An availability window has: **day of week (0 = Sunday … 6 = Saturday)**, **start/end time**, **recurring** flag (repeats weekly), and **effectiveFrom/effectiveUntil** dates. Booking validates weekday + time-inside-window + effective dates on the server, even though the UI already guides the patient.

## 14. Admin Module

All under `/api/v1/admin/**`, ADMIN enforced by `SecurityConfig`.

- **Specialties:** list (incl. inactive), create, edit, deactivate — duplicate names → 409; nothing hard-deleted.
- **Doctors:** create = **one transaction**: `users` row (BCrypt password) + DOCTOR role + `doctors` profile; any failure rolls back all three (tested). Duplicate email/license → 409. Admins cannot create admins through this API.
- **Availability:** create/edit/close windows per doctor; validations: valid day, start < end, effectiveUntil ≥ effectiveFrom, duplicate window → 409.

Frontend admin pages live at `/admin/...` behind an `AdminRoute` guard (UX only — the backend is the real gate) and use the same design system as the patient area.

## 15. Appointment Status and Audit History

Valid transitions (enforced server-side; invalid → 409):

```
SCHEDULED → CONFIRMED → COMPLETED
SCHEDULED → CANCELLED        CONFIRMED → CANCELLED        CONFIRMED → NO_SHOW
```

`COMPLETED`, `CANCELLED`, `NO_SHOW` are terminal — no further transitions.

Example audit rows from a verified run:

```
appointment 2711:
  SCHEDULED  changed_by: patient user  "Appointment created"
  CONFIRMED  changed_by: doctor user   "Appointment confirmed by doctor"
  COMPLETED  changed_by: doctor user   "Visit completed"
```

**`changed_by_user_id`** records the authenticated user resolved from the JWT — never a value from the request. It answers "who did this?" months later. NULL actors only occur for internal, non-HTTP records.

## 16. Frontend Pages

| Page | Route | Access | Purpose | Main APIs |
|---|---|---|---|---|
| Homepage | `/` | public | Project landing page | — |
| Login | `/login` | public | Sign-in | `POST /auth/login` |
| Register | `/register` | public | Account creation | `POST /auth/register` |
| Dashboard | `/dashboard` | signed-in | Role-aware: patient view or doctor view | `GET /appointments/me` |
| Book Appointment | `/appointments/new` | patient | 3-step wizard: specialty → doctor → date/time → confirm | `/specialties`, `/doctors`, `/doctors/{id}/availability`, `POST /appointments` |
| My Appointments | `/appointments/me` | patient | Status filter tabs, responsive cards | `GET /appointments/me` |
| Appointment Detail | `/appointments/:id` | patient/doctor | Summary, status badge, fee, cancel modal; doctors see Confirm/Complete/No-Show | `GET /appointments/{id}` + lifecycle endpoints |
| Doctor Appointments | `/doctor/appointments` | doctor | Doctor's own list | `GET /appointments/me` |
| Admin Dashboard | `/admin` | admin | Navigation | — |
| Specialties Admin | `/admin/specialties` | admin | Table + modals + deactivate | `/admin/specialties` |
| Doctors Admin | `/admin/doctors` | admin | Create/edit doctor (account + profile) | `/admin/doctors` |
| Availability Admin | `/admin/availability` | admin | Per-doctor windows | `/admin/doctors/{id}/availability` |

## 17. Implemented Features

- ✅ Java 21 + Spring Boot 4.1.1 + MySQL; React 19 + Vite + TypeScript; both builds green
- ✅ Flyway V1 (9 schema tables), `ddl-auto=validate`, 10 live tables
- ✅ JPA entities + repositories incl. locking read (`findByIdForUpdate`)
- ✅ Secure self-booking, availability validation, conflict rejection (409), status transitions, cancellation rules
- ✅ REST API: auth, discovery, appointments (me/get/create/cancel/confirm/complete/no-show), admin CRUD — all DTO-based
- ✅ Security: register/login, BCrypt, JWT (60 min), role rules, ownership checks, clean 401/403/404/409 errors
- ✅ Design system (tokens, Button/Card/Modal/Toast/Badges/Skeletons), role-aware AppShell, all 12 pages
- ✅ Admin: specialty/doctor/availability management incl. transactional doctor creation
- ✅ Doctor portal: dashboard, list, confirm/complete/no-show with dialogs + toasts
- ✅ Audit: status history with real `changed_by_user_id`
- ✅ Tests: backend **230 / 0 failures** (real MySQL + real JWTs); frontend **50** + production build

## 18. Current Limitations and Future Improvements

**Planned but not implemented:**
- Patient/doctor profile editing (profiles are read-only)
- Refresh tokens (JWT expires after 60 minutes; re-login is required)
- Notifications (email/SMS), payments, messaging

**Nice-to-have:**
- Admin dashboard statistics (intentionally no fabricated counts)
- Doctor-managed availability
- Search/filter refinements in admin lists

**Production hardening:**
- HTTPS, secrets management (DB password & JWT secret are dev-configured)
- Pagination on list endpoints
- CORS hardening & rate limiting
- CI pipeline

**Small known items:**
- Patient/doctor identity in list rows shows as IDs (e.g. "Patient #1350") — responses carry IDs, not embedded names
- "Today" on the doctor dashboard uses the browser's clock/timezone

## 19. Test Data and Setup

Current development database state: minimal (test data is cleaned after verification runs). Setup is done through the application itself:

| Data | How to create |
|---|---|
| Patient | Register via `/register` (creates user + PATIENT role + profile) |
| Admin | Register, then grant once via SQL (no admin self-registration by design): `INSERT INTO user_roles (user_id, role_id) SELECT <userId>, id FROM roles WHERE name='ADMIN';` |
| Specialty | Admin → Specialties → create |
| Doctor | Admin → Doctors → Add Doctor (login + profile in one step) |
| Availability | Admin → Availability → window for the relevant weekday (Mon=1 … Sun=0) |
| Appointment | Patient → Book Appointment |

Passwords used locally are development/test-only.

**Cleanup (FK-safe order):** history → appointments → availability → doctors → patients → user_roles → users → specialties.

## 20. Application Walkthrough

**Step 1 — Start MySQL**
Action: verify the MySQL service is running and the `medbook` schema exists.
Expected result: the sanity query returns 10 tables.

**Step 2 — Start the backend**
Action: `cd backend && mvn spring-boot:run`.
Expected result: startup log completes; `/actuator/health` returns `{"status":"UP"}`.

**Step 3 — Start the frontend**
Action: `cd frontend && npm run dev`.
Expected result: `http://localhost:5173` loads the homepage, styled.

**Step 4 — Admin setup**
Action: register an account, grant ADMIN via SQL, log in, create a specialty, a doctor, and an availability window covering the demo date's weekday.
Expected result: the new records appear in the admin tables.

**Step 5 — Patient registration**
Action: register a patient account at `/register`.
Expected result: redirect to the patient dashboard.

**Step 6 — Patient authentication**
Action: log in with the patient account.
Expected result: dashboard is displayed; the JWT is stored for authenticated API requests.

**Step 7 — Discovery and booking**
Action: Book Appointment → select specialty → select doctor → select date → select time from the availability chips → submit.
Expected result: appointment created (201); detail page shows SCHEDULED.

**Step 8 — Conflict prevention**
Action: attempt to book the same doctor/date/time again (second browser tab).
Expected result: 409 conflict with a readable message; no duplicate row.

**Step 9 — My Appointments**
Action: open `/appointments/me`.
Expected result: the new appointment listed, filter tabs show real counts.

**Step 10 — Cancellation**
Action: open the appointment → Cancel → enter a reason → confirm.
Expected result: status CANCELLED; reason displayed; further transitions rejected.

**Step 11 — Audit history inspection**
Action: run `SELECT * FROM appointment_status_history ORDER BY id DESC LIMIT 6;`
Expected result: rows for created/cancelled with the real `changed_by_user_id`.

**Step 12 — Doctor login**
Action: log out, log in with the doctor account.
Expected result: doctor dashboard with today/upcoming visits.

**Step 13 — Status transitions**
Action: open an assigned SCHEDULED appointment → Confirm → then Complete.
Expected result: badge changes with each transition (dialog + toast); terminal state removes the action buttons.

**Step 14 — Admin management**
Action: log in as admin → manage specialties/doctors/availability.
Expected result: tables update immediately; patient discovery reflects the changes.

## 21. Backup / Recovery

| Problem | Recovery |
|---|---|
| Backend won't start | Check port: `netstat -ano | grep :8080` → kill stale process (`taskkill //F //PID <pid>`). Check MySQL is up. Read the last Maven console lines. |
| MySQL down / access denied | Start the MySQL service; verify credentials in `application.properties` (`DB_USERNAME`/`DB_PASSWORD`). |
| Frontend blank/unstyled | Hard refresh (Ctrl+Shift+R); restart `npm run dev`. |
| 401 everywhere | Token expired (60 min) → log in again. |
| 403 on admin pages | Account lacks ADMIN role → rerun the grant SQL and log in again. |
| 409 when booking | Slot taken or outside availability → pick another time (intended behavior). |
| 500 | Read the backend console — the exception is printed there; restart the backend. |
| Test data missing | Redo setup steps in §19/§20. |
| Full failure during a session | Run the test suites (`mvn clean test`, `npm test`) — they verify the system against real MySQL. |

## 22. Development Commands

```bash
# Backend (port 8080)
cd backend
mvn spring-boot:run          # start
mvn clean test               # all backend tests (real MySQL required)

# Frontend (port 5173)
cd frontend
npm run dev                  # dev server
npm test                     # component tests
npm run build                # production build

# Health checks
curl http://localhost:8080/actuator/health     # {"status":"UP"}
curl http://localhost:8080/api/v1/hello        # welcome JSON

# Database
mysql -uroot -proot medbook
SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='medbook';  -- 10
SELECT version, success FROM flyway_schema_history;                            -- 1, 1
SELECT * FROM appointment_status_history ORDER BY id DESC LIMIT 10;
```

## 23. Testing

| Layer | Tooling | Scope |
|---|---|---|
| Backend integration | JUnit + Spring Test + MockMvc, real MySQL, real JWTs | Auth, ownership (cross-user denial), booking conflicts, status transitions, concurrency (two-thread race), admin CRUD, transactional rollback |
| Frontend | Vitest + Testing Library | Page rendering, loading/empty/error states, role-aware actions, form validation, dialog flows |

Current totals: **backend 230 tests / 0 failures / 0 errors**, frontend **50 tests**, production build passing.

## 24. Common Errors

| Problem | Likely cause | Fix |
|---|---|---|
| `ECONNREFUSED` | Backend not running | `cd backend && mvn spring-boot:run` |
| API calls fail, frontend fine | Wrong port / backend down | Check 5173 vs 8080; hit `/actuator/health` |
| 401 | Missing/expired JWT | Log in again |
| 403 | Wrong role or wrong owner | Correct account, or expected rejection |
| 404 | Wrong ID or another user's resource | Check the ID |
| 409 | Business conflict (double book, duplicate name/slot, invalid transition) | Intended behavior — adjust input |
| MySQL access denied | Wrong credentials/env | Check `application.properties` / env vars |
| Port 8080 in use | Old Spring process | `netstat -ano | grep :8080` → kill PID |
| Styles missing | Dev server stale | Restart `npm run dev`; check `index.css` import |
| Hibernate validate fails | Entity/schema mismatch | Schema is source of truth — fix the entity |

## 25. Common Technical Questions

**What does Spring Boot do in this project?**
It is the backend framework: it hosts the REST API, routes requests to controllers, applies Spring Security, manages transactions, and connects to MySQL.

**Why was MySQL chosen?**
The data is naturally relational (users→roles, doctors→appointments) and the project relies on transactions and constraints (uniqueness, CHECKs) that relational databases enforce.

**What is JPA?**
A Java specification for mapping objects to database tables, so most data access is written as Java objects and interface methods rather than SQL strings.

**What is Hibernate?**
The JPA implementation used here — it generates SQL from the entities and Spring Data repository interfaces.

**What is Flyway and why is it used?**
A schema migration tool: numbered SQL files applied once each, in order. The whole schema is version 1 and reproducible from an empty database.

**What is a JWT?**
A signed token issued at login. The client sends it on every request; the server verifies the signature and expiry without storing sessions (stateless authentication).

**Why BCrypt for passwords?**
Passwords must never be stored in plain text. BCrypt is a slow, salted one-way hash designed for passwords, so even the database cannot reveal them.

**What is a REST API?**
HTTP endpoints organized around resources (`/doctors`, `/appointments`) using standard verbs and status codes (200/201/400/401/403/404/409).

**Why React?**
Component reuse and a reactive UI — data changes update the screen without full page reloads.

**Why TypeScript?**
Types catch shape mismatches (e.g. a missing field in an API response) at compile time instead of at runtime.

**What happens when an appointment is booked?**
The server resolves the patient from the JWT, validates doctor/patient/availability, locks rows, checks conflicts, saves with a fee snapshot, and writes the SCHEDULED audit row. Returns 201.

**How is double booking prevented?**
A pessimistic row lock (`SELECT … FOR UPDATE`) inside the transaction serializes concurrent requests; the second one re-reads the committed state and fails with 409. An automated test runs two real concurrent requests to verify it.

**How does the server know who the patient is?**
The JWT's subject is the user ID; `CurrentUserProvider` resolves it from the security context — never from request data.

**How is one patient prevented from viewing another patient's appointment?**
The service compares the appointment's patient with the authenticated user before returning anything; mismatch → 403. List endpoints are built from the token as well.

**Why is status history needed?**
Every change is recorded with who made it and when (`changed_by_user_id`) — an audit trail for accountability and debugging.

**What happens when a doctor is deactivated?**
They disappear from patient discovery and cannot be booked; existing appointment rows keep pointing at them (no data loss).

**How does admin create a doctor?**
One transaction: user row (hashed password) + DOCTOR role + doctor profile — all-or-nothing, covered by a test.

**How does the frontend talk to the backend?**
Axios HTTP+JSON with the JWT header; React Query caches responses and handles loading/error/refetching.

**What happens if the backend is down?**
React Query shows a "cannot reach the server" state with retry; the UI does not crash.

**How is the project tested?**
230 backend integration tests against real MySQL with real JWTs (auth, ownership, transitions, concurrency, admin), 50 frontend component tests, plus full-stack walkthrough runs.

**Why store a fee snapshot?**
A doctor's later price change must not rewrite what past appointments recorded.

**What does `ddl-auto=validate` do?**
Hibernate only verifies entities match the schema; Flyway owns schema changes. Mismatches stop startup instead of corrupting data.

**Why is cancellation a POST with a reason?**
It is a state-changing operation, and the reason is required data recorded in the audit trail.

**What does "stateless sessions" mean here?**
The server stores no login session; every request is authenticated by the token itself.

**Where does authorization happen — frontend or backend?**
The frontend hides buttons (UX), but the backend re-checks role and ownership on every request. The server is the security boundary.

## 26. Architecture Diagram

```
        User (browser)
           ↓ clicks
        React (SPA, port 5173)
           ↓ Axios  +  "Authorization: Bearer <JWT>"
   ┌───────────────────────────────────────┐
   │  Spring Boot REST API (port 8080)     │
   │     ↓                                 │
   │  Spring Security  ← JWT verified here │
   │     ↓                                 │
   │  Service layer  ← business rules      │
   │     ↓                                 │
   │  Repository / JPA (Hibernate)         │
   └───────────────────────────────────────┘
           ↓ SQL
        MySQL  (Flyway-managed schema)
```

## 27. Quick Reference

- **Purpose:** patients book real doctor slots; doctors manage visits; admin manages clinic data.
- **Stack:** React 19 + TS + Tailwind v4 + React Query + Axios | Java 21 + Spring Boot 4.1.1 + JPA + Security | MySQL + Flyway.
- **Roles:** PATIENT (book/cancel own), DOCTOR (confirm/complete/no-show own schedule), ADMIN (specialties/doctors/availability).
- **Architecture:** React → Axios(+JWT) → Security → Service → JPA → MySQL.
- **Security:** BCrypt hashes, 60-min JWTs, role rules in SecurityConfig, ownership in services, 401/403/404/409 semantics.
- **Database:** 9 schema tables (+Flyway's = 10), fee snapshots, status history with `changed_by_user_id`.
- **Status:** all listed features implemented and verified — 230 backend tests / 50 frontend tests, builds green.
- **Next up:** profile editing, refresh tokens, notifications, payments, deployment hardening.
