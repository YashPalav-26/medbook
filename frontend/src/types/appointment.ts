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

/** GET /api/v1/specialties — specialty (admin list also returns inactive rows) */
export interface SpecialtyResponse {
  id: number;
  name: string;
  description: string | null;
  active: boolean;
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
  id: number | null;
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

/**
 * ADMIN clinic-wide appointment row (GET /api/v1/admin/appointments[?filters]).
 * Mirrors backend AdminAppointmentResponse — includes participant display names
 * and the doctor's specialty, which the patient/doctor-facing shape does not carry.
 */
export interface AdminAppointmentResponse {
  id: number;
  patientId: number;
  patientName: string;
  doctorId: number;
  doctorName: string;
  specialty: string;
  appointmentDate: string;
  startTime: string;
  endTime: string;
  status: AppointmentStatus;
  cancellationReason: string | null;
  consultationFeeSnapshot: number;
}

/** One status-change audit row of the admin detail timeline. */
export interface AppointmentStatusHistoryResponse {
  id: number;
  status: AppointmentStatus;
  notes: string | null;
  changedByName: string | null;
  changedAt: string; // ISO-8601 timestamp
}

/** GET /api/v1/admin/appointments/:id — appointment plus its audit trail. */
export interface AdminAppointmentDetailResponse {
  appointment: AdminAppointmentResponse;
  history: AppointmentStatusHistoryResponse[];
}

/**
 * ADMIN patient row (GET /api/v1/admin/patients). Mirrors backend PatientAdminResponse —
 * read-only account linkage used for the booking-on-behalf picker. No address, insurance
 * or medical fields: the admin list answers "who is this patient and how do we reach them".
 */
export interface PatientAdminResponse {
  id: number;
  email: string;
  firstName: string;
  lastName: string;
  phoneNumber: string;
  dateOfBirth: string; // ISO date
  active: boolean;
  userActive: boolean;
}

/** GET /api/v1/admin/appointments/overview — real dashboard metrics. */
export interface AdminOverviewResponse {
  todayAppointments: number;
  today: string;
  statusCounts: Record<AppointmentStatus, number>;
  totalAppointments: number;
}

/** Query params of GET /api/v1/admin/appointments — every filter optional. */
export interface AdminAppointmentFilters {
  date?: string;
  status?: AppointmentStatus | '';
  doctorId?: number | '';
  patientId?: number;
}