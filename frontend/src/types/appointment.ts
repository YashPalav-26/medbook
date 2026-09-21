/**
 * Types mirroring the backend appointment DTOs exactly.
 * Source of truth: backend/src/main/java/com/medbook/dto
 */

export type AppointmentStatus = 'SCHEDULED' | 'CONFIRMED' | 'COMPLETED' | 'CANCELLED' | 'NO_SHOW';

/**
 * Body of POST /api/v1/appointments — a PATIENT self-booking.
 * Deliberately no patientId: the backend books for the JWT caller.
 */
export interface CreateAppointmentRequest {
  doctorId: number;
  appointmentDate: string; // ISO date: "2026-10-01"
  startTime: string;       // "HH:mm" format
  endTime: string;         // "HH:mm" format
  reason?: string;         // optional visit reason
}

/**
 * Body of POST /api/v1/appointments/admin — the ADMIN support path.
 * This is the only booking route where a patientId is accepted.
 */
export interface AdminCreateAppointmentRequest {
  patientId: number;
  doctorId: number;
  appointmentDate: string;
  startTime: string;
  endTime: string;
}

/** GET /api/v1/specialties — active specialty */
export interface SpecialtyResponse {
  id: number;
  name: string;
  description: string | null;
}

/** GET /api/v1/doctors — booking-facing doctor profile */
export interface DoctorSummaryResponse {
  id: number;
  firstName: string;
  lastName: string;
  specialty: string;
  yearsExperience: number;
  bio: string | null;
  consultationFee: number;
  profilePictureUrl: string | null;
}

/** GET /api/v1/doctors/:id/availability — one scheduling window */
export interface DoctorAvailabilityResponse {
  dayOfWeek: number; // 0 = Sunday ... 6 = Saturday (MySQL convention)
  startTime: string; // "HH:mm"
  endTime: string;   // "HH:mm"
  recurring: boolean;
  effectiveFrom: string | null;
  effectiveUntil: string | null;
}

/** Body of POST /api/v1/appointments/:id/cancel */
export interface CancelAppointmentRequest {
  cancellationReason: string;
}

/** Backend AppointmentResponse */
export interface AppointmentResponse {
  id: number;
  patientId: number;
  doctorId: number;
  appointmentDate: string;
  startTime: string;
  endTime: string;
  status: AppointmentStatus;
  cancellationReason: string | null;
  consultationFeeSnapshot: number;
}