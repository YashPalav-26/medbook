import type { AxiosResponse } from 'axios';
import type {
  SpecialtyResponse,
  DoctorAvailabilityResponse,
} from '../types/appointment';
import { api } from './client';

/**
 * Admin management endpoints (ADMIN role required — enforced by the backend).
 * Shapes mirror the backend admin DTOs exactly.
 */

/** A doctor in the admin list/detail view: profile plus account linkage. */
export interface DoctorAdminResponse {
  id: number;
  email: string;
  firstName: string;
  lastName: string;
  phoneNumber: string;
  specialtyId: number;
  specialty: string;
  medicalLicenseNumber: string;
  yearsExperience: number;
  bio: string | null;
  consultationFee: number;
  profilePictureUrl: string | null;
  active: boolean;
  userActive: boolean;
}

export interface SpecialtyCreateRequest {
  name: string;
  description?: string;
}

export interface SpecialtyUpdateRequest {
  name: string;
  description?: string;
}

export interface DoctorCreateRequest {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  phoneNumber: string;
  specialtyId: number;
  medicalLicenseNumber: string;
  yearsExperience: number;
  bio?: string;
  consultationFee: number;
  profilePictureUrl?: string;
}

export interface DoctorUpdateRequest {
  specialtyId: number;
  medicalLicenseNumber: string;
  yearsExperience: number;
  bio?: string;
  consultationFee: number;
  profilePictureUrl?: string;
}

export interface AvailabilityCreateRequest {
  dayOfWeek: number;
  startTime: string;
  endTime: string;
  recurring?: boolean;
  effectiveFrom?: string;
  effectiveUntil?: string;
}

export interface AvailabilityUpdateRequest {
  dayOfWeek: number;
  startTime: string;
  endTime: string;
  recurring?: boolean;
  effectiveFrom?: string;
  effectiveUntil?: string;
}

// ------------------------------------------------------------------
// Specialties
// ------------------------------------------------------------------

export async function listAdminSpecialties(): Promise<SpecialtyResponse[]> {
  const response: AxiosResponse<SpecialtyResponse[]> = await api.get('/admin/specialties');
  return response.data;
}

export async function createSpecialty(payload: SpecialtyCreateRequest): Promise<SpecialtyResponse> {
  const response: AxiosResponse<SpecialtyResponse> = await api.post('/admin/specialties', payload);
  return response.data;
}

export async function updateSpecialty(id: number, payload: SpecialtyUpdateRequest): Promise<SpecialtyResponse> {
  const response: AxiosResponse<SpecialtyResponse> = await api.put(`/admin/specialties/${id}`, payload);
  return response.data;
}

export async function deactivateSpecialty(id: number): Promise<SpecialtyResponse> {
  const response: AxiosResponse<SpecialtyResponse> =
    await api.patch(`/admin/specialties/${id}/deactivate`);
  return response.data;
}

// ------------------------------------------------------------------
// Doctors
// ------------------------------------------------------------------

export async function listAdminDoctors(): Promise<DoctorAdminResponse[]> {
  const response: AxiosResponse<DoctorAdminResponse[]> = await api.get('/admin/doctors');
  return response.data;
}

export async function getAdminDoctor(id: number): Promise<DoctorAdminResponse> {
  const response: AxiosResponse<DoctorAdminResponse> = await api.get(`/admin/doctors/${id}`);
  return response.data;
}

export async function createDoctor(payload: DoctorCreateRequest): Promise<DoctorAdminResponse> {
  const response: AxiosResponse<DoctorAdminResponse> = await api.post('/admin/doctors', payload);
  return response.data;
}

export async function updateDoctor(id: number, payload: DoctorUpdateRequest): Promise<DoctorAdminResponse> {
  const response: AxiosResponse<DoctorAdminResponse> = await api.put(`/admin/doctors/${id}`, payload);
  return response.data;
}

export async function deactivateDoctor(id: number): Promise<DoctorAdminResponse> {
  const response: AxiosResponse<DoctorAdminResponse> =
    await api.patch(`/admin/doctors/${id}/deactivate`);
  return response.data;
}

// ------------------------------------------------------------------
// Availability
// ------------------------------------------------------------------

export async function listAdminAvailability(doctorId: number): Promise<DoctorAvailabilityResponse[]> {
  const response: AxiosResponse<DoctorAvailabilityResponse[]> =
    await api.get(`/admin/doctors/${doctorId}/availability`);
  return response.data;
}

export async function createAvailability(
  doctorId: number,
  payload: AvailabilityCreateRequest,
): Promise<DoctorAvailabilityResponse> {
  const response: AxiosResponse<DoctorAvailabilityResponse> =
    await api.post(`/admin/doctors/${doctorId}/availability`, payload);
  return response.data;
}

export async function updateAvailability(
  id: number,
  payload: AvailabilityUpdateRequest,
): Promise<DoctorAvailabilityResponse> {
  const response: AxiosResponse<DoctorAvailabilityResponse> =
    await api.put(`/admin/availability/${id}`, payload);
  return response.data;
}

export async function deactivateAvailability(id: number): Promise<DoctorAvailabilityResponse> {
  const response: AxiosResponse<DoctorAvailabilityResponse> =
    await api.patch(`/admin/availability/${id}/deactivate`);
  return response.data;
}
