/**
 * Types mirroring the backend appointment DTOs exactly.
 * Source of truth: backend/src/main/java/com/medbook/dto
 */

export type AppointmentStatus = 'SCHEDULED' | 'CONFIRMED' | 'COMPLETED' | 'CANCELLED' | 'NO_SHOW';

/** Body of POST /api/v1/appointments — book a new appointment */
export interface CreateAppointmentRequest {
  patientId: number;
  doctorId: number;
  appointmentDate: string; // ISO date: "2026-10-01"
  startTime: string;       // "HH:mm" format
  endTime: string;         // "HH:mm" format
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