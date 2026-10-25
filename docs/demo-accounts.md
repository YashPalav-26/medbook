# MedBook — Demo & Development Accounts

This is the single source of truth for the accounts you use to click through the
Patient, Doctor and Admin experiences in development. None of these exist in
production, and none are created by a migration.

All three roles use the **same** login page — <http://localhost:5173/login>. Which
experience you get is decided by the roles inside your account, never by the URL
you logged in from.

---

## Quick reference

| Role | Email | Password | How the account is created |
|---|---|---|---|
| Patient | `patient@medbook.test` | `Patient@123` | Auto-registered by the dev seed (or self-register at `/register`) |
| Doctor | `doctor@medbook.test` | `Doctor@123` | Auto-registered by the dev seed (or Admin → Doctors → New Doctor) |
| Admin | `admin@medbook.test` | `Admin@123` | Auto-registered by the dev seed (or the bootstrap below) |

These are **obviously fake development credentials**, deliberately committed so every
developer gets the same logins. Never reuse them anywhere real, and never point the
dev seed at a production database.

---

## How the accounts are configured

There are two independent mechanisms. You normally want the **dev seed** for local
work, and the **bootstrap** to provision the first admin in a real environment.

### 1. Development seed (local, one command)

The seed runs only when `medbook.demo.seed=true`. The `dev` Spring profile sets that
for you, so the simplest way to start a fully populated environment is:

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

or, equivalently, set the environment variable and run the default profile:

```bash
MEDBOOK_DEMO_SEED=true mvn spring-boot:run
```

On a fresh database the seeder creates the Admin, Doctor and Patient above plus two
specialties, a full week of doctor availability and a few realistic appointments, so
the Doctor and Admin dashboards are not empty. It is idempotent — running it again
against an already-seeded database changes nothing, and the demo admin is only
created if no administrator exists yet.

**Production:** leave `medbook.demo.seed` unset. The seeder bean is not even created,
so no demo data can ever be inserted.

### 2. First-admin bootstrap (any environment)

When you need a real first administrator and do not want dev demo data, set:

```text
MEDBOOK_BOOTSTRAP_ADMIN_EMAIL      # the admin's sign-in email
MEDBOOK_BOOTSTRAP_ADMIN_PASSWORD   # the admin's password (BCrypt-hashed on save)
MEDBOOK_BOOTSTRAP_ADMIN_FIRST_NAME # optional, defaults to "Clinic"
MEDBOOK_BOOTSTRAP_ADMIN_LAST_NAME  # optional, defaults to "Administrator"
MEDBOOK_BOOTSTRAP_ADMIN_PHONE_NUMBER # optional, defaults to "+10000000000"
```

(Each also has a Spring property equivalent, e.g. `medbook.bootstrap.admin-email`.)

The bootstrap is **scoped to the configured email**: on startup, if that exact email
is not yet registered, the application creates it with the ADMIN role (BCrypt-hashed
password). If the account already exists it is returned untouched — no duplicate, no
password overwrite, no duplicate role — even when other administrators exist. With
the email/password unset the bootstrap is simply disabled.

In this repository `backend/src/main/resources/application.properties` ships the
development/setup credentials (the configured initial administrator, **Yash Palav**
— see that file). The database only ever stores the BCrypt hash; no log or API ever
returns the plaintext.

This is server-side only: there is no HTTP endpoint for it, the password never comes
from the browser, and the plaintext password is never logged.

**Production:** override the `medbook.bootstrap.admin-*` properties (or their
`MEDBOOK_BOOTSTRAP_ADMIN_*` environment variables) with your own values, or leave
email/password blank to disable bootstrap provisioning entirely. Do not ship real
credentials to shared repositories — the values in `application.properties` are the
explicitly requested development/setup configuration.

---

## Verifying each role

| Role | Steps |
|---|---|
| Patient | Sign in as `patient@medbook.test` → lands on the patient dashboard → Book Appointment, My Appointments, Profile. Or register a brand-new account at `/register`. |
| Doctor | Sign in as `doctor@medbook.test` → lands on the doctor dashboard → confirm / complete / no-show appointments. |
| Admin | Sign in as `admin@medbook.test` (or the configured initial administrator) → lands on the clinic overview → manage Specialties, Doctors, Patients, Availability, clinic-wide Appointments and other Admins (**Admins** page). |

To test "admin creates a doctor": sign in as the admin, open **Doctors → New Doctor**,
fill in the form, then sign out and sign in with that new doctor's email/password.
The new account receives the `DOCTOR` role and reaches the doctor dashboard.

---

## If a demo account is missing

- The dev seed only creates the **admin** demo account when no administrator exists.
  If your database already has a different admin, `admin@medbook.test` is skipped. Use
  your existing admin, or remove it (dev only) and restart.
- The seeder never overwrites an existing account by email, so a manually-registered
  `patient@medbook.test` keeps its own password.
- Check the backend startup log: the seeder logs `Demo seed complete.` and the
  bootstrap logs whether it created an administrator or skipped.
