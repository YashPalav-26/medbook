# MedBook — Application Demo Checklist

*Quick-reference checklist for running and demonstrating the application.*

---

## 0. PRE-DEMO CHECKLIST

- [ ] **MySQL running** — test:
  ```bash
  mysql -uroot -proot medbook -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='medbook';"
  ```
  Expected: `10`.
- [ ] **Backend running** (port 8080):
  ```bash
  cd backend
  mvn spring-boot:run
  ```
  Wait for `Started MedbookBackendApplication`. Then:
  ```bash
  curl http://localhost:8080/actuator/health     # {"status":"UP"}
  curl http://localhost:8080/api/v1/hello        # welcome JSON
  ```
- [ ] **Frontend running** (port 5173):
  ```bash
  cd frontend
  npm run dev
  ```
  Open http://localhost:5173 — homepage loads styled (teal buttons, not raw HTML).
- [ ] **Browser open** at `http://localhost:5173` (a second tab is useful for the conflict-prevention check).
- [ ] **Accounts ready** (see §1 below — create them if missing).
- [ ] **Data ready**: ≥1 specialty, ≥1 doctor, ≥1 availability window covering the demo date's weekday, patient registered.
- [ ] **History query ready** (copy-paste into a terminal):
  ```bash
  mysql -uroot -proot medbook -e "SELECT id, appointment_id, status, changed_by_user_id, notes FROM appointment_status_history ORDER BY id DESC LIMIT 6;"
  ```
- [ ] **Ports confirmed**: 8080 (backend) and 5173 (frontend) — nothing else squatting on them.

---

## 1. ACCOUNTS AND DATA SETUP

> Credentials below are **development/test-only**. There are no admin accounts by
> default — registration always creates a PATIENT; the ADMIN role is granted once
> via SQL (dev bootstrap).

| Account / data | How to create | Values |
|---|---|---|
| Admin | Register at `/register`, then: `mysql -uroot -proot medbook -e "INSERT INTO user_roles (user_id, role_id) SELECT (SELECT id FROM users WHERE email='admin@demo.test'), id FROM roles WHERE name='ADMIN';"` | `admin@demo.test` / chosen password |
| Patient | Register at `/register` | `patient@demo.test` / chosen password |
| Doctor | Log in as admin → Doctors → Add Doctor (creates login + profile in one step) | email/password entered in the form |
| Specialty | Admin → Specialties → create (e.g. "Cardiology") | — |
| Availability | Admin → Availability → pick the doctor → window 09:00–17:00 for the demo date's weekday (Mon=1, Tue=2, Wed=3, Thu=4, Fri=5, Sat=6, Sun=0) | — |

Verify the admin grant: log in as admin → **Admin Area** card visible on the dashboard.

---

## 2. WALKTHROUGH SEQUENCE

| # | Action | Expected result |
|---|---|---|
| 1 | Open `http://localhost:5173/` | Homepage renders |
| 2 | Sign in as **patient** | Patient dashboard, personal greeting |
| 3 | Book Appointment | 3-step wizard: specialty → doctor → date/time |
| 4 | Pick doctor → date | Doctor card shows fee/experience; time chips come from availability |
| 5 | Submit booking | Toast + detail page (SCHEDULED badge) |
| 6 | My Appointments | Appointment listed; status filter tabs show counts |
| 7 | Second tab: repeat the same slot | 409 conflict message; no duplicate row |
| 8 | Detail → Cancel with a reason | Status → CANCELLED, reason displayed |
| 9 | Run the history SQL from §0 | History rows with real user IDs |
| 10 | Sign out → sign in as **doctor** | Doctor dashboard (today/upcoming) |
| 11 | Open a SCHEDULED appointment → **Confirm** | Dialog → badge CONFIRMED → toast |
| 12 | → **Mark Completed** | Badge COMPLETED; action buttons gone (terminal state) |
| 13 | Sign out → sign in as **admin** | Dashboard with Admin Area card |
| 14 | Specialties → create/edit | Table updates instantly |
| 15 | Doctors → Add Doctor | Doctor appears in table |
| 16 | Availability → create/close a window | Open/Closed badges update |
| 17 | Architecture diagram (project guide §26) | — |

---

## 3. FALLBACK COMMANDS

```bash
# Who is blocking port 8080?
netstat -ano | grep :8080
taskkill //F //PID <pid>

# Restart everything fresh
cd backend && mvn spring-boot:run        # terminal 1
cd frontend && npm run dev               # terminal 2

# Health
curl http://localhost:8080/actuator/health
curl http://localhost:8080/api/v1/hello

# Database sanity
mysql -uroot -proot medbook -e "SELECT version, success FROM flyway_schema_history;"
```

**Troubleshooting during a session:**

| Symptom | What it means / do |
|---|---|
| 401 | Token expired after 60 minutes — log in again (normal) |
| 409 when booking | Conflict guard working — pick another slot (intended behavior) |
| Admin card missing | ADMIN grant SQL wasn't run for that account — rerun §1 SQL, log in again |
| Frontend frozen | Hard refresh (Ctrl+Shift+R); restart `npm run dev` if needed |
| Backend down | Restart backend; `mvn clean test` output verifies system health |

---

## 4. NOTES

1. Complete all setup before starting the walkthrough.
2. Rejections (401/403/409) during the walkthrough are the security and validation rules working as designed.
3. Never display database credentials or `application.properties` secrets on screen.
4. The Quick Reference section of `project-showcase-guide.md` is a compact summary of the whole system.
