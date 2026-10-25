import type { AxiosResponse } from 'axios';
import type {
  SpecialtyResponse,
  DoctorAvailabilityResponse,
  AdminAppointmentResponse,
  AdminAppointmentDetailResponse,
  AdminOverviewResponse,
  AdminAppointmentFilters,
  PatientAdminResponse,
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

export interface AdminCreateRequest {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  phoneNumber: string;
}

/** An administrator account in the Admin Management view — never any password field. */
export interface AdminAccountResponse {
  id: number;
  email: string;
  firstName: string;
  lastName: string;
  phoneNumber: string;
  active: boolean;
  locked: boolean;
  roles: string[];
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
// Administrators (admin account management)
// ------------------------------------------------------------------

/** Every administrator account with role names (GET /admin/admins). */
export async function listAdminAccounts(): Promise<AdminAccountResponse[]> {
  const response: AxiosResponse<AdminAccountResponse[]> = await api.get('/admin/admins');
  return response.data;
}

/** Create an administrator (POST /admin/admins); the backend assigns ADMIN server-side. */
export async function createAdminAccount(payload: AdminCreateRequest): Promise<AdminAccountResponse> {
  const response: AxiosResponse<AdminAccountResponse> = await api.post('/admin/admins', payload);
  return response.data;
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
// Patients (read-only — patients self-register; admins book on their behalf)
// ------------------------------------------------------------------

/** Every patient (active and inactive) with account linkage (GET /admin/patients). */
export async function listAdminPatients(): Promise<PatientAdminResponse[]> {
  const response: AxiosResponse<PatientAdminResponse[]> = await api.get('/admin/patients');
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

// ------------------------------------------------------------------
// Appointments (clinic-wide visibility + support cancellation)
// ------------------------------------------------------------------

/** Build a clean query string: empty/undefined filters are never sent. */
function appointmentParams(filters: AdminAppointmentFilters): Record<string, string | number> {
  const params: Record<string, string | number> = {};
  if (filters.date) params.date = filters.date;
  if (filters.status) params.status = filters.status;
  if (filters.doctorId) params.doctorId = filters.doctorId;
  if (filters.patientId) params.patientId = filters.patientId;
  return params;
}

/**
 * Clinic-wide appointment list with optional filters (GET /admin/appointments).
 * Filters are applied server-side — the backend does the querying, not JavaScript.
 */
export async function listAdminAppointments(
  filters: AdminAppointmentFilters = {},
): Promise<AdminAppointmentResponse[]> {
  const response: AxiosResponse<AdminAppointmentResponse[]> =
    await api.get('/admin/appointments', { params: appointmentParams(filters) });
  return response.data;
}

/** One appointment with its full status-change audit trail (GET /admin/appointments/:id). */
export async function getAdminAppointment(id: number): Promise<AdminAppointmentDetailResponse> {
  const response: AxiosResponse<AdminAppointmentDetailResponse> =
    await api.get(`/admin/appointments/${id}`);
  return response.data;
}

/** Real clinic overview metrics for the dashboard (GET /admin/appointments/overview). */
export async function getAdminOverview(): Promise<AdminOverviewResponse> {
  const response: AxiosResponse<AdminOverviewResponse> =
    await api.get('/admin/appointments/overview');
  return response.data;
}

/**
 * ADMIN support cancellation (POST /admin/appointments/:id/cancel).
 * A reason is required; the backend records the authenticated admin as the actor.
 */
export async function cancelAppointmentAsAdmin(
  id: number,
  cancellationReason: string,
): Promise<AdminAppointmentResponse> {
  const response: AxiosResponse<AdminAppointmentResponse> =
    await api.post(`/admin/appointments/${id}/cancel`, { cancellationReason });
  return response.data;
}
