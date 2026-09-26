# Profile and Account Management (Step 24)

> Audience: anyone reading MedBook for the first time. No prior knowledge of the
> authentication or profile design is assumed.

## What this feature does

Every signed-in user can now open **My Profile** (`/profile`) and:

- see their own account details (name, email, phone, roles);
- **patients** additionally see and edit their medical/contact profile
  (address, emergency contact, insurance, allergies);
- **doctors** see their professional profile and edit exactly two fields
  (bio and profile picture URL) — everything registry-like stays with the admin;
- every role can **change their own password** (current password required).

This replaced the old static "Your details" card on the patient dashboard with a
real page backed by a real API.

## The current user: where identity comes from

Every request carries a JWT (the one issued at login) in the
`Authorization: Bearer` header. The backend reads the token's `sub` claim through
`CurrentUserProvider` and treats **that** as the caller. There is no endpoint
anywhere in the profile API that accepts a user ID — not in the path, not in the
body, not as a query parameter. That is why "a patient can only see their own
profile" is not a check the code performs per request; it is a structural fact:
the only profile the API can address is the caller's.

The profile endpoints live under `/api/v1/me/**`, which the security
configuration covers with its `anyRequest().authenticated()` rule: no valid
token → **401** from the authentication entry point.

## The API

| Endpoint | What it does |
|---|---|
| `GET /api/v1/me/profile` | The caller's profile: account block + role block |
| `PUT /api/v1/me/account` | Update own name and phone (every role) |
| `PUT /api/v1/me/patient-profile` | Update own patient profile (patients only) |
| `PUT /api/v1/me/doctor-profile` | Update own bio + picture (doctors only) |
| `POST /api/v1/me/change-password` | Change own password (every role) |

One `GET` serves all roles: the response always carries the account fields, plus
a `patient` block when the caller has a patient row, plus a `doctor` block when
the caller has a doctor row. An admin (who is neither) sees only the account
block — the page shows a short note instead of an empty section.

## The response shape (CurrentUserResponse)

```json
{
  "id": 1,
  "email": "john@medbook.test",
  "firstName": "John",
  "lastName": "Carter",
  "phoneNumber": "+919810000001",
  "roles": ["ROLE_PATIENT"],
  "patient": {
    "patientId": 4096,
    "dateOfBirth": "1990-04-12",
    "addressLine1": "42 Marine Drive",
    "city": "Mumbai",
    "emergencyContactName": "Rita Carter",
    "hasInsurance": false,
    "allergies": null
  },
  "doctor": null
}
```

**Never serialized:** `password_hash`, `failed_login_attempts`, `is_locked`,
`last_login_at`, and the generated audit timestamps. The response is a DTO
(`CurrentUserResponse` with nested `PatientProfileResponse` /
`DoctorProfileResponse` records) — JPA entities are never returned, so there is
no path from the API to a security internal.

## Editable vs admin-controlled fields

The split follows what the application already enforced before this step — no
new policy was invented:

**Account block** (every role, self-service):
- `firstName`, `lastName`, `phoneNumber` — editable.
- `email` — deliberately **not** editable. It is the sign-in identity and the
  uniqueness anchor every lookup keys on; changing it is account recovery
  territory, not profile editing.
- `roles`, `is_active`, `is_locked`, `failed_login_attempts` — system-controlled,
  not part of any update request (the DTO has no such fields).

**Patient profile** (the `patients` row linked to the caller):
- Editable: address lines, city, state, postal code, country, emergency contact
  name/phone, insurance provider/policy + the has-insurance flag, allergies.
- Not editable by the patient: `date_of_birth` (registration/medical identity
  data), `is_active` (system).
- The update is a **full-form PUT** (the project's convention, same as
  `DoctorUpdateRequest`): every field is required in the payload, and an empty
  string clears the optional column. Missing fields fail validation with 400
  rather than silently wiping data.

**Doctor profile** (the `doctors` row linked to the caller):
- **Doctor-editable:** `bio`, `profilePictureUrl` — how the doctor describes
  their own practice.
- **Admin-controlled:** specialty, medical license number, consultation fee,
  active flag. These are managed by clinic administration through
  `/api/v1/admin/doctors` (Step 20's `DoctorAdminService`) because they are
  registry data the clinic owns. The doctor update DTO simply has no fields for
  them, so a hostile payload carrying `"consultationFee": 1.00` changes nothing
  — proven by a dedicated test that sends exactly that and asserts the stored
  values are untouched.

## Cross-user protection

Because the profile row is always resolved from the JWT subject
(`patientRepository.findByUserId(currentUser)`,
`doctorRepository.findByUserId(currentUser)`), there is no way to address
another user's data. Attempting the wrong route for your role fails cleanly:

- a doctor calling `PUT /me/patient-profile` → **403** (no patient row exists for them);
- a patient calling `PUT /me/doctor-profile` → **403**;
- an anonymous call → **401**.

Integration tests verify the caller's update touches only their own row and
leaves a second fixture user's row unchanged.

## Password change

`POST /api/v1/me/change-password` with `{ "currentPassword", "newPassword" }`:

1. The caller is resolved from the JWT.
2. The **current password is verified with BCrypt** against the stored hash —
   possession of a valid JWT alone is not enough, so a stolen token cannot lock
   the real owner out. Wrong current password → **401**.
3. The new password (8–72 characters, BCrypt's limits) is stored **only as a
   BCrypt hash**. Plaintext never touches storage or logs; the request record
   falls out of scope after the encoder calls.
4. Success returns **204**.

**Token lifetime limitation (documented, not hidden):** MedBook's JWTs are
stateless and there is no refresh-token or revocation mechanism, so **existing
tokens stay valid until they expire** even after a password change. The UI says
this explicitly in the success message ("Your current session stays signed
in"). Token revocation would require server-side token state — a deliberate
scope boundary for this project.

## The frontend page

`/profile` reuses the MedBook design system: `AppShell` layout, section `Card`s,
`PageHeader`-style headings, `Input`/`Select` fields, `StatusBadge`-style pills
via text, `ErrorBox`, `Skeleton`, and `ToastProvider` for success/error
feedback.

- **Sections, not one giant form:** Account, Patient Profile *or* Professional
  Profile, (Administrator note for admins), Change Password.
- Each section is **read-only until "Edit"** is pressed; the form pre-fills from
  the loaded profile, and Cancel returns to display mode without saving.
- Editing uses **React Hook Form + Zod** with the same limits as the backend
  (lengths, phone pattern, password 8–72). The submit button is disabled while
  saving; backend validation errors render inside the form via `toApiError`, so
  nothing the user typed is lost.
- **Change Password** has three fields (current, new, confirm), a visibility
  toggle per field (a11y-labelled), a "Passwords do not match" check, and an
  inline success banner plus toast. A wrong current password shows the backend's
  401 message and keeps the form filled.
- **React Query hooks** (`useMyProfile`, `useUpdateAccount`,
  `useUpdatePatientProfile`, `useUpdateDoctorProfile`, `useChangePassword`) own
  all caching. A successful account update also syncs the AuthContext user (via
  a small `updateUser` patch) so the header shows the new name immediately —
  there is no duplicate profile state anywhere.
- **Navigation:** "My Profile" appears in the sidebar for patients, doctors and
  admins, and the dashboard's old "Your details" card became a clickable
  "My Profile" card that routes to `/profile` (client-side, no reloads).

## Testing

**Backend — `MeProfileTest` (20 tests, real MySQL, real JWTs, rolled-back
transactions):** authenticated reads for all three roles; the security internals
absent from the response; patient profile update persisted to the real table
(and `date_of_birth` untouched); validation failures (400 with field errors);
doctor cannot update a patient profile and vice versa; doctor update restricted
to bio/picture with a hostile-payload test proving fee/license/specialty/active
survive; cross-user isolation for both profile types; account update with email
immutability; password change success (new hash verifies, old password stops
working, plaintext absent from storage), wrong current password (401, hash
unchanged), short new password (400), missing current password (400),
unauthenticated (401), and appointments unaffected by a password change.

**Frontend — `ProfilePage.test.tsx` (13 tests):** account rendering for every
role; patient fields with date of birth; doctor fields with the admin-controlled
markers; the administrator note; profile load error with retry; account edit
flow (pre-filled form, client validation, successful save with toast, cancel
without saving, backend error keeping the form open); patient full-form update;
doctor edit exposing only bio/picture; password change (mismatch blocked,
success, short password rejected, 401 wrong-current-password surfaced while
keeping the form).

## How to verify it by hand (3 minutes)

1. Sign in as a patient → sidebar **My Profile** → account and patient sections
   appear with real data.
2. Press **Edit** on Patient Profile, change the city, **Save Changes** → toast,
   the display view shows the new city, and a page reload keeps it (persisted).
3. Change Password with current + new + confirm → success message; signing out
   and back in requires the **new** password.
4. Sign in as a doctor → Professional Profile shows specialty/license/fee marked
   "managed by clinic administration"; Edit offers only bio and picture.
5. As any non-owner role, call another role's update endpoint → 403.

## Current limitations

- **Email changes are not supported** (deliberate — sign-in identity).
- **No token revocation:** a password change does not invalidate existing JWTs;
  they simply expire on schedule.
- **No profile picture upload:** the doctor picture is a URL field; file storage
  is out of scope.
- **Admin has no dedicated profile section** beyond the account block — the
  application has no admin profile model, and none was invented.
- **No audit trail on profile changes:** unlike appointment status changes,
  profile edits are not written to a history table (the schema has no such table
  and Step 24 does not create one).
