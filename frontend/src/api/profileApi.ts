import type { AxiosResponse } from 'axios';
import type {
  CurrentUserResponse,
  AccountUpdateRequest,
  PatientProfileUpdateRequest,
  DoctorProfileUpdateRequest,
  ChangePasswordRequest,
} from '../types/auth';
import { api } from './client';

export async function getMyProfile(): Promise<CurrentUserResponse> {
  const response: AxiosResponse<CurrentUserResponse> = await api.get('/me/profile');
  return response.data;
}

export async function updateMyAccount(payload: AccountUpdateRequest): Promise<CurrentUserResponse> {
  const response: AxiosResponse<CurrentUserResponse> = await api.put('/me/account', payload);
  return response.data;
}

export async function updateMyPatientProfile(
  payload: PatientProfileUpdateRequest,
): Promise<CurrentUserResponse> {
  const response: AxiosResponse<CurrentUserResponse> = await api.put('/me/patient-profile', payload);
  return response.data;
}

export async function updateMyDoctorProfile(
  payload: DoctorProfileUpdateRequest,
): Promise<CurrentUserResponse> {
  const response: AxiosResponse<CurrentUserResponse> = await api.put('/me/doctor-profile', payload);
  return response.data;
}

export async function changeMyPassword(payload: ChangePasswordRequest): Promise<void> {
  await api.post('/me/change-password', payload);
}
