import type { AxiosResponse } from 'axios';
import type {
  CurrentUserResponse,
  AccountUpdateRequest,
  PatientProfileUpdateRequest,
  DoctorProfileUpdateRequest,
  ChangePasswordRequest,
} from '../types/auth';
import { api } from './client';

/**
 * Own-profile endpoints ({@code /api/v1/me/**}). Shapes mirror the backend DTOs
 * exactly. No user ID is ever sent — the backend resolves the caller from the JWT.
 */

/** GET /me/profile — account block plus the role-specific profile if one exists. */
export async function getMyProfile(): Promise<CurrentUserResponse> {
  const response: AxiosResponse<CurrentUserResponse> = await api.get('/me/profile');
  return response.data;
}

/** PUT /me/account — name + phone, every role. */
export async function updateMyAccount(payload: AccountUpdateRequest): Promise<CurrentUserResponse> {
  const response: AxiosResponse<CurrentUserResponse> = await api.put('/me/account', payload);
  return response.data;
}

/** PUT /me/patient-profile — the patient self-service fields (full form). */
export async function updateMyPatientProfile(
  payload: PatientProfileUpdateRequest,
): Promise<CurrentUserResponse> {
  const response: AxiosResponse<CurrentUserResponse> = await api.put('/me/patient-profile', payload);
  return response.data;
}

/** PUT /me/doctor-profile — bio + picture (the doctor-editable slice). */
export async function updateMyDoctorProfile(
  payload: DoctorProfileUpdateRequest,
): Promise<CurrentUserResponse> {
  const response: AxiosResponse<CurrentUserResponse> = await api.put('/me/doctor-profile', payload);
  return response.data;
}

/** POST /me/change-password — 204 on success, 401 on wrong current password. */
export async function changeMyPassword(payload: ChangePasswordRequest): Promise<void> {
  await api.post('/me/change-password', payload);
}
