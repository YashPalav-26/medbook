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

export async function cancelAppointment(id: number, payload: CancelAppointmentRequest): Promise<AppointmentResponse> {
  const response: AxiosResponse<AppointmentResponse> = await api.post(`/appointments/${id}/cancel`, payload);
  return response.data;
}