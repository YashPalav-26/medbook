import type { AxiosResponse } from 'axios';
import type { AppointmentResponse, CreateAppointmentRequest, CancelAppointmentRequest } from '../types/appointment';
import { api } from './client';

/**
 * Appointment endpoints. Shapes mirror the backend DTOs exactly.
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