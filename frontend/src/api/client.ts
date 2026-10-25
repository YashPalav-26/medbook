import axios, { AxiosError } from 'axios';
import type { ErrorResponse, FieldError } from '../types/auth';
import { clearAuth, getStoredToken } from '../lib/storage';












export const API_BASE_URL: string = import.meta.env.VITE_API_BASE_URL ?? '/api/v1';

export const api = axios.create({
  baseURL: API_BASE_URL,
  headers: { 'Content-Type': 'application/json' },
});

api.interceptors.request.use((config) => {
  const token = getStoredToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ErrorResponse>) => {
    if (error.response?.status === 401) {
      clearAuth();
      redirectToLogin();
    }
    return Promise.reject(error);
  },
);

function redirectToLogin(): void {
  if (typeof window === 'undefined' || window.location.pathname.startsWith('/login')) {

    return;
  }
  window.location.assign('/login');
}


export interface ApiError {
  status?: number;
  message: string;
  fieldErrors: FieldError[];
}


export const INVALID_CREDENTIALS_MESSAGE = 'Invalid email or password';







export function toApiError(error: unknown): ApiError {
  if (axios.isAxiosError(error)) {
    const backendError = error.response?.data as ErrorResponse | undefined;

    if (backendError?.message) {
      return {
        status: error.response?.status,
        message: backendError.message,
        fieldErrors: backendError.fieldErrors ?? [],
      };
    }

    if (!error.response) {
      return {
        message: 'Cannot reach the server. Please check that the backend is running.',
        fieldErrors: [],
      };
    }

    return {
      status: error.response.status,
      message: `Request failed with status ${error.response.status}`,
      fieldErrors: [],
    };
  }

  return { message: 'Something went wrong. Please try again.', fieldErrors: [] };
}
