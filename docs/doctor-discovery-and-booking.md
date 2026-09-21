# Doctor Discovery, Availability & Secure Booking (Step 18)

This document explains how a patient finds a doctor and books an appointment, and
why the booking flow no longer asks for a patient ID.

---

## 1. Why doctor discovery APIs exist

Before this step, the booking form asked the patient to type a **numeric doctor ID**
(such as `1` or `1500`). That is not how people choose healthcare:

- patients do not know internal database IDs;
- nothing stopped them from typing a doctor who does not exist, is inactive, or
  works a different specialty than they assumed;
- the UI could show nothing about *who* the doctor is, so the patient confirmed a
  booking blind.

Step 18 adds three small read-only APIs so the booking form can offer real choices:

| Endpoint | Returns | Used for |
|---|---|---|
| `GET /api/v1/specialties` | active specialties (id, name, description) | specialty dropdown |
| `GET /api/v1/doctors?specialtyId=` | active doctors (id, name, specialty, experience, bio, fee, photo) | doctor dropdown, doctor card |
| `GET /api/v1/doctors/{id}/availability?date=` | scheduling windows (day, start, end, recurring, effective range) | showing which times are possible |

All three require authentication (they sit under the security configuration's
`anyRequest().authenticated()`), and all three return **DTOs** — never JPA entities.
That matters because a doctor's entity graph leads straight to the linked `users`
row, which carries the **password hash** and login bookkeeping. The DTO cuts that
edge off on purpose: the API simply has no field where a hash could leak.

There is deliberately **no doctor CRUD** here. Managing doctor profiles is an
administrative feature that does not exist yet; this step only *reads*.

---

## 2. Specialty filtering

The booking form works in narrowing steps:

1. **Pick a specialty** → `GET /specialties` fills the dropdown (active only,
   alphabetical).
2. **Pick a doctor** → `GET /doctors?specialtyId=5` returns only *active* doctors
   of that specialty. A deactivated doctor can never appear in the list, so the
   patient cannot select someone who cannot take appointments.
3. **Pick a date** → `GET /doctors/{id}/availability?date=2026-09-28` returns only
   windows *in force that day* (correct weekday, inside the effective-from/until
   range). A one-off (non-recurring) window matches only its single effective day.

React Query keeps this efficient: each dropdown has its own query key
(`['specialties']`, `['doctors', specialtyId]`, `['doctorAvailability', doctorId, date]`),
so changing the specialty refetches only the doctor list, and results are cached
rather than re-fetched on every keystroke.

---

## 3. Availability lookup vs. booking validation

This split is important:

- **The availability API is guidance.** It answers "which windows *could* work?"
  so the patient does not waste a submission on an impossible time.
- **The booking endpoint is the authority.** When the form is submitted,
  `AppointmentService` re-checks *everything* against the database inside one
  transaction: doctor exists, doctor active, patient exists, patient active,
  window inside availability, weekday match, effective dates, no overlapping
  appointment, plus a `SELECT ... FOR UPDATE` lock on the doctor row so two
  simultaneous bookings cannot both win.

No scheduling rule is duplicated in JavaScript. If the UI and the backend ever
disagree, the backend wins — the worst a stale UI can do is cause a rejected
request with a clear 400/404/409 message, never a wrong booking.

---

## 4. Authenticated patient resolution (the security fix)

### The old, unsafe way

`POST /appointments` used to accept a `patientId` field from the request body.
Whoever could log in could then write **any** patient ID on the form and create
appointments for strangers — or have a compromised client silently substitute one.
The server had no way to know whether the ID belonged to the caller.

### The new rule

The patient's identity now comes **only from the JWT**:

```
JWT (sub = user id) → users row → patient profile linked to that user
```

The patient self-booking body contains **no patientId at all**:

```json
{
  "doctorId": 7,
  "appointmentDate": "2026-09-28",
  "startTime": "09:00",
  "endTime": "09:30",
  "reason": "checkup (optional)"
}
```

Inside `AppointmentService.createAppointmentForCurrentUser`:

1. the caller's role is checked — only PATIENT may self-book (a doctor has no
   patient profile; admins use the support path);
2. the patient profile linked to the authenticated user is loaded
   (`patientRepository.findByUserId(...)`);
3. that profile — never anything from the request — becomes the appointment's
   patient.

Consequence: even a hostile client that stuffs `"patientId": 9999` into the body
cannot change the outcome. The extra field is ignored (the DTO has no such
property), and the appointment belongs to the caller. This is verified by
`patientSelfBookingIgnoresAClientSuppliedPatientId`, which smuggles another
patient's ID into the body and asserts the booking still lands on the caller.

### The admin support path (kept, restricted, justified)

Real clinics book over the phone. `POST /api/v1/appointments/admin` accepts a
`patientId` — but **only ADMIN callers** may use it, and the capability is the
point of the endpoint: an administrator booking on behalf of a patient who cannot
use the app. A patient who calls this route gets **403**
("Booking for another patient requires an administrator"), not a booking.

### Doctor booking

Doctors do not book appointments in this project, so the capability is simply
absent: a doctor calling either booking route receives 403. If a future step
introduces doctor-initiated booking, it will be an explicit, tested decision —
not a silent default.

---

## 5. Backend/frontend responsibility split

| Concern | Backend | Frontend |
|---|---|---|
| Who the patient is | resolves from the JWT | never asks, never sends |
| Who may book/cancel | enforced per role | may *hide* buttons, hides nothing security-relevant |
| Valid availability | re-checks at submit | shows windows as guidance |
| Slot conflicts | detected + 409 | displays a readable message |
| Expired session | 401 | auth handling |

"Hiding the button in React is not authorization" — every rule in the right-hand
column is a convenience; every rule in the middle column is the actual security.

---

## 6. The booking flow, end to end

1. Patient opens `/appointments/new` (already signed in).
2. Specialty dropdown ← `GET /specialties`.
3. Doctor dropdown ← `GET /doctors?specialtyId=…`; selecting a doctor shows a card
   with name, specialty, experience, bio and consultation fee.
4. Date chosen → availability chips ← `GET /doctors/{id}/availability?date=…`.
   No windows that day? The form says so plainly instead of letting the patient
   submit into a guaranteed 409.
5. Start/end times entered; a summary line shows the full booking before submit.
6. Submit → `POST /appointments` (self-booking body, no patientId) → **201** with
   a `Location` header → navigate to `/appointments/{id}`.
7. Backend errors surface as readable messages: 400 validation, 401 expired
   session, 403 forbidden, 404 unknown doctor, 409 conflict/inactive/overlap.

The patient is never asked for their own ID, and nothing in the UI can change
whose appointment it becomes.

---

## 7. Tests that prove the security property

Backend (`AppointmentControllerTest`, new section):

- `patientSelfBookingCreatesAppointmentForTheAuthenticatedPatient` — booking lands
  on the JWT caller's patient profile, not another patient row;
- `patientSelfBookingIgnoresAClientSuppliedPatientId` — a smuggled foreign
  `patientId` is ignored;
- `patientSelfBookingIsRejectedForDoctorAndAdminCallers` — wrong roles get 403;
- `adminSupportBookingIsRejectedForPatientCallers` — patients cannot use the
  support path;
- `patientSelfBookingRecordsTheCallerInStatusHistory` — the SCHEDULED history row
  records the real caller's user ID (`changed_by_user_id`);
- validation (400) and unknown-doctor (404) paths for the new body shape;
- the legacy tests now exercise the admin support path through
  `POST /appointments/admin` under the ADMIN fixture caller.

Frontend (`BookAppointmentPage.test.tsx`):

- specialties and doctors load from the real API contracts;
- availability renders for the chosen date (and a clear empty state when none);
- **no patient ID input exists anywhere on the page**;
- validation blocks submission until specialty/doctor/date/time are chosen;
- the submission payload contains **no `patientId` property at all**;
- 409 and 403 responses render as readable messages;
- the booking summary shows doctor, specialty, date/time and fee.

---

## 8. Scope notes

- No new tables, no migration — discovery reads the existing
  `specialties`, `doctors`, `doctor_availability` rows.
- The admin support path reuses the original service rules (lock, activity checks,
  availability, conflicts), so both booking routes share one implementation of the
  business logic.
- `reason` is a new *field on the request*, stored in the existing
  `appointments.reason` column (500 chars) — a convenience for the clinic, optional.
