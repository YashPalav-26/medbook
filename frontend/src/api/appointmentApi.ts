import type { AxiosResponse } from 'axios';
import type {
  AppointmentResponse,
  CreateAppointmentRequest,
  CancelAppointmentRequest,
  SpecialtyResponse,
  DoctorSummaryResponse,
  DoctorAvailabilityResponse,
} from '../types/appointment';
import { api } from './client';

/**
 * Appointment + discovery endpoints. Shapes mirror the backend DTOs exactly.
 */

/**
 * Book an appointment as the authenticated PATIENT (POST /appointments).
 * No patientId is sent — the backend books for the JWT caller.
 */
export async function createAppointment(payload: CreateAppointmentRequest): Promise<AppointmentResponse> {
  const response: AxiosResponse<AppointmentResponse> = await api.post('/appointments', payload);
  return response.data;
}

export async function getAppointment(id: number): Promise<AppointmentResponse> {
  const response: AxiosResponse<AppointmentResponse> = await api.get(`/appointments/${id}`);
  return response.data;
}

/**
 * The authenticated caller's appointments (GET /appointments/me).
 *
 * The backend decides what "mine" means from the JWT (patient → booked by me,
 * doctor → assigned to me); no user ID is sent from the client.
 */
export async function getMyAppointments(): Promise<AppointmentResponse[]> {
  const response: AxiosResponse<AppointmentResponse[]> = await api.get('/appointments/me');
  return response.data;
}

export async function cancelAppointment(id: number, payload: CancelAppointmentRequest): Promise<AppointmentResponse> {
  const response: AxiosResponse<AppointmentResponse> = await api.post(`/appointments/${id}/cancel`, payload);
  return response.data;
}

/** Active specialties for the booking dropdown (GET /specialties). */
export async function getSpecialties(): Promise<SpecialtyResponse[]> {
  const response: AxiosResponse<SpecialtyResponse[]> = await api.get('/specialties');
  return response.data;
}

/** Active doctors, optionally filtered by specialty (GET /doctors?specialtyId=...). */
export async function getDoctors(specialtyId?: number): Promise<DoctorSummaryResponse[]> {
  const response: AxiosResponse<DoctorSummaryResponse[]> =
    await api.get('/doctors', { params: specialtyId ? { specialtyId } : undefined });
  return response.data;
}

/** A doctor's availability, optionally for one date (GET /doctors/:id/availability?date=...). */
export async function getDoctorAvailability(doctorId: number, date?: string): Promise<DoctorAvailabilityResponse[]> {
  const response: AxiosResponse<DoctorAvailabilityResponse[]> =
    await api.get(`/doctors/${doctorId}/availability`, { params: date ? { date } : undefined });
  return response.data;
}