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
