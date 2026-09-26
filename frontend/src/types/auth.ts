/**
 * Types mirroring the backend auth DTOs exactly.
 * Source of truth: backend/src/main/java/com/medbook/dto (Step 14).
 */

/** Body of POST /api/v1/auth/register — patient-only registration. */
export interface RegisterRequest {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  phoneNumber: string;
  /** ISO date string, e.g. "1990-01-31". */
  dateOfBirth: string;
}

/** Body of POST /api/v1/auth/login. */
export interface LoginRequest {
  email: string;
  password: string;
}

/** The signed-in user's public profile, as returned by the backend. */
export interface AuthUser {
  id: number;
  email: string;
  firstName: string;
  lastName: string;
  /** Spring Security authorities, e.g. ["ROLE_PATIENT"]. */
  roles: string[];
}

/** Backend AuthResponse: a JWT plus the user it belongs to. */
export interface AuthResponse {
  token: string;
  /** Always "Bearer" today. */
  tokenType: string;
  expiresInMinutes: number;
  user: AuthUser;
}

/** One rejected request field, as produced by Bean Validation on the backend. */
export interface FieldError {
  field: string;
  message: string;
}

/** Shape of every backend error response (see GlobalExceptionHandler). */
export interface ErrorResponse {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  fieldErrors: FieldError[];
}

/** A field name on the register request, used to map backend field errors onto inputs. */
export type RegisterRequestField = keyof RegisterRequest;

// ------------------------------------------------------------------
// Profile + account management (Step 24) — mirrors backend /api/v1/me DTOs
// ------------------------------------------------------------------

/** GET /api/v1/me/profile — the caller's own profile with the role-specific block. */
export interface CurrentUserResponse {
  id: number;
  email: string;
  firstName: string;
  lastName: string;
  phoneNumber: string;
  roles: string[];
  patient: PatientProfileResponse | null;
  doctor: DoctorProfileResponse | null;
}

/** The patient medical/contact block (fields the patient may edit marked in the UI). */
export interface PatientProfileResponse {
  patientId: number;
  dateOfBirth: string; // ISO date
  addressLine1: string | null;
  addressLine2: string | null;
  city: string | null;
  stateProvince: string | null;
  postalCode: string | null;
  country: string | null;
  emergencyContactName: string | null;
  emergencyContactPhone: string | null;
  hasInsurance: boolean;
  insuranceProvider: string | null;
  insurancePolicyNumber: string | null;
  allergies: string | null;
}

/** The doctor professional block, split into editable and admin-controlled. */
export interface DoctorProfileResponse {
  doctorId: number;
  // doctor-editable
  bio: string | null;
  profilePictureUrl: string | null;
  // admin-controlled (read-only here)
  specialtyName: string;
  medicalLicenseNumber: string;
  consultationFee: number;
  active: boolean;
}

/** PUT /api/v1/me/account — full form, all fields required. */
export interface AccountUpdateRequest {
  firstName: string;
  lastName: string;
  phoneNumber: string;
}

/** PUT /api/v1/me/patient-profile — full form; "" clears an optional column. */
export interface PatientProfileUpdateRequest {
  addressLine1: string;
  addressLine2: string;
  city: string;
  stateProvince: string;
  postalCode: string;
  country: string;
  emergencyContactName: string;
  emergencyContactPhone: string;
  hasInsurance: boolean;
  insuranceProvider: string;
  insurancePolicyNumber: string;
  allergies: string;
}

/** PUT /api/v1/me/doctor-profile — the doctor-editable slice only. */
export interface DoctorProfileUpdateRequest {
  bio: string;
  profilePictureUrl: string;
}

/** POST /api/v1/me/change-password. */
export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}
