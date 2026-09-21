import type { AxiosResponse } from 'axios';
import type { AuthResponse, LoginRequest, RegisterRequest } from '../types/auth';
import { api } from './client';

/**
 * Authentication endpoints. Shapes mirror the backend DTOs exactly — no invented fields,
 * no extra endpoints (the backend has no refresh-token endpoint to call).
 */

export async function register(payload: RegisterRequest): Promise<AuthResponse> {
  const response: AxiosResponse<AuthResponse> = await api.post('/auth/register', payload);
  return response.data;
}

export async function login(payload: LoginRequest): Promise<AuthResponse> {
  const response: AxiosResponse<AuthResponse> = await api.post('/auth/login', payload);
  return response.data;
}
