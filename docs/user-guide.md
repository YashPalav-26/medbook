# MedBook — Simple User Guide

*A plain-language guide to what MedBook is and how to use it. No technical knowledge needed.*

---

## What is MedBook?

MedBook is a website for a clinic where **patients book appointments with doctors**.

In short:

- **Patients** pick a doctor, see when the doctor is free, and book a time.
- **Doctors** see the appointments booked with them and mark them confirmed, completed, or missed.
- **Administrators (admins)** set up the clinic: the medical departments, the doctors, and each doctor's working hours.

Everything happens in the browser. There is nothing to install.

---

## The three types of users

| User | What they do in MedBook |
|---|---|
| **Patient** | Registers, books appointments, cancels their own appointments |
| **Doctor** | Logs in, sees their schedule, confirms and closes visits |
| **Admin** | Logs in, manages specialties, doctors, and working hours |

Patients create their own account on the website.
Doctors and admins are set up through the Admin area (see below).

---

## Getting started

1. Open the website (during development this is `http://localhost:5173`).
2. You land on the **homepage**. Use **Sign In** to log in, or **Register** to create a patient account.
3. After logging in you always land on your **Dashboard**, with a menu on the left for everything you can do.
4. **Sign Out** is always in the top-right corner.

**Note:** if the site asks you to log in again after a while, that is normal — sessions expire after about an hour for security.

---

## Using MedBook as a Patient

### 1. Create an account
- Click **Register**.
- Fill in your name, email, phone number, date of birth, and a password.
- Click submit — you are logged in straight away.

### 2. Book an appointment
1. Click **Book Appointment** in the menu.
2. **Choose a specialty** — the medical department you need (for example Cardiology).
3. **Choose a doctor** — you see each doctor's specialty, experience, bio, and consultation fee.
4. **Choose a date.**
5. **Pick a time** — the free time slots for that doctor and day are shown as buttons. If the doctor has no working hours that day, the page says so.
6. Check the **summary** (doctor, date, time, fee) and click the booking button.

That's it. The appointment now appears in **My Appointments** with the status **SCHEDULED**.

> You never enter a patient number anywhere. The system knows who you are from your login.

### 3. See your appointments
- Click **My Appointments**.
- Your appointments are listed with their status. Use the filter buttons (All / Upcoming / Cancelled) to find things faster.
- Click any appointment to see the full details: doctor, date, time, fee, and status.

### 4. Cancel an appointment
1. Open the appointment from **My Appointments**.
2. Click **Cancel Appointment**.
3. A window asks for a **reason** and warns you that cancelling cannot be undone.
4. Confirm. The status changes to **CANCELLED** and your reason is stored with it.

Cancelling is only possible while an appointment is **SCHEDULED** or **CONFIRMED**. Once the doctor has completed the visit (or marked it as a no-show), it can no longer be changed.

---

## Using MedBook as a Doctor

Doctors log in the same way as patients (an account is created for them by an admin).

### Your dashboard
- Shows **today's appointments** and **upcoming** ones.
- Click **My Appointments** in the menu to see every appointment booked with you.

### Working through your appointments
Open an appointment to see its details. The buttons you see depend on its current state:

| Appointment state | What you can do |
|---|---|
| **SCHEDULED** | **Confirm** — you are confirming the visit will happen |
| **CONFIRMED** | **Mark Completed** (the visit happened) or **Mark No-Show** (the patient didn't arrive) |
| **COMPLETED / CANCELLED / NO SHOW** | Nothing — the appointment is closed |

Each action asks you to confirm first, and the status badge on the page updates immediately.

---

## Using MedBook as an Admin

Admins log in like everyone else. Their dashboard shows an **Admin Area** card with three sections.

### 1. Specialties
- Add the clinic's medical departments (e.g. Cardiology).
- Edit a specialty's name/description, or **Deactivate** it so patients can no longer choose it.
- The same specialty name cannot be added twice.

### 2. Doctors
- Click **Add Doctor** and fill in the form: email + password (their login), name, phone, specialty, license number, years of experience, bio, and consultation fee.
- This single form creates **both** the doctor's login account and their public profile.
- Edit a doctor's profile anytime, or **Deactivate** them — deactivated doctors disappear from the booking page but their past appointments are kept.

### 3. Availability
- Pick a doctor and create their **working hours**: day of the week, start time, end time.
- Mark a window as **Repeats** if it happens every week (usually yes).
- **Effective from / until** dates can limit a window to a period (for example a doctor on leave).
- **Deactivate** a window when the doctor stops working that slot.
- These are exactly the time slots patients see when booking.

---

## What the appointment statuses mean

| Status | Meaning |
|---|---|
| **SCHEDULED** | Booked, waiting for the doctor to confirm |
| **CONFIRMED** | The doctor confirmed the visit will happen |
| **COMPLETED** | The visit took place |
| **CANCELLED** | Cancelled (the reason is shown on the appointment) |
| **NO SHOW** | The patient did not arrive |

---

## Messages you might see

| Message | What it means | What to do |
|---|---|---|
| "Doctor … is not available on … between …" | The chosen time is outside the doctor's working hours | Pick another time slot |
| A message that the slot is already taken (409) | Someone booked that exact slot first | Pick another slot — this is the system protecting you from double bookings |
| "Email is already registered" | An account with that email exists | Log in instead, or use another email |
| "Cannot reach the server" | The backend isn't running right now | Try again in a moment |
| "Access denied" | You tried to open a page for a different role (e.g. an admin page as a patient) | Go back — each role only sees its own area |
| Asked to log in again | Your session expired (~1 hour) | Log in again, nothing was lost |

---

## Quick reference — where everything is

| I want to… | Go to |
|---|---|
| Book an appointment | Menu → **Book Appointment** |
| See/cancel my appointments | Menu → **My Appointments** |
| See my schedule (doctor) | Menu → **My Appointments** |
| Confirm/complete a visit (doctor) | Open the appointment → action buttons |
| Manage specialties/doctors/hours (admin) | Dashboard → **Admin Area** |
| Log out | Top-right → **Sign Out** |
