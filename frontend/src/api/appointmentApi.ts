import type { AxiosResponse } from 'axios';
import type {
  AppointmentResponse,
  CreateAppointmentRequest,
  AdminCreateAppointmentRequest,
  CancelAppointmentRequest,
  SpecialtyResponse,
  DoctorSummaryResponse,
  DoctorAvailabilityResponse,
} from '../types/appointment';
import { api } from './client';

export async function createAppointment(payload: CreateAppointmentRequest): Promise<AppointmentResponse> {
  const response: AxiosResponse<AppointmentResponse> = await api.post('/appointments', payload);
  return response.data;
}

export async function createAppointmentForPatient(
  payload: AdminCreateAppointmentRequest,
): Promise<AppointmentResponse> {
  const response: AxiosResponse<AppointmentResponse> = await api.post('/appointments/admin', payload);
  return response.data;
}

export async function getAppointment(id: number): Promise<AppointmentResponse> {
  const response: AxiosResponse<AppointmentResponse> = await api.get(`/appointments/${id}`);
  return response.data;
}

export async function getMyAppointments(): Promise<AppointmentResponse[]> {
  const response: AxiosResponse<AppointmentResponse[]> = await api.get('/appointments/me');
  return response.data;
}

export async function cancelAppointment(id: number, payload: CancelAppointmentRequest): Promise<AppointmentResponse> {
  const response: AxiosResponse<AppointmentResponse> = await api.post(`/appointments/${id}/cancel`, payload);
  return response.data;
}

export async function confirmAppointment(id: number): Promise<AppointmentResponse> {
  const response: AxiosResponse<AppointmentResponse> = await api.post(`/appointments/${id}/confirm`);
  return response.data;
}

export async function completeAppointment(id: number): Promise<AppointmentResponse> {
  const response: AxiosResponse<AppointmentResponse> = await api.post(`/appointments/${id}/complete`);
  return response.data;
}

export async function markAppointmentNoShow(id: number): Promise<AppointmentResponse> {
  const response: AxiosResponse<AppointmentResponse> = await api.post(`/appointments/${id}/no-show`);
  return response.data;
}

export async function getSpecialties(): Promise<SpecialtyResponse[]> {
  const response: AxiosResponse<SpecialtyResponse[]> = await api.get('/specialties');
  return response.data;
}

export async function getDoctors(specialtyId?: number): Promise<DoctorSummaryResponse[]> {
  const response: AxiosResponse<DoctorSummaryResponse[]> =
    await api.get('/doctors', { params: specialtyId ? { specialtyId } : undefined });
  return response.data;
}

export async function getDoctorAvailability(doctorId: number, date?: string): Promise<DoctorAvailabilityResponse[]> {
  const response: AxiosResponse<DoctorAvailabilityResponse[]> =
    await api.get(`/doctors/${doctorId}/availability`, { params: date ? { date } : undefined });
  return response.data;
}