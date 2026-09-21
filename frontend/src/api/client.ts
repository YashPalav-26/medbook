import axios, { AxiosError } from 'axios';
import type { ErrorResponse, FieldError } from '../types/auth';
import { clearAuth, getStoredToken } from '../lib/storage';

/**
 * The single Axios instance for the whole app.
 *
 * - baseURL comes from the environment (VITE_API_BASE_URL); with `npm run dev` the default
 *   is the same-origin path `/api/v1`, which Vite proxies to Spring Boot (no CORS needed).
 * - the request interceptor attaches the stored JWT as a Bearer token when there is one.
 * - the response interceptor clears invalid authentication after any 401 and sends the user
 *   back to /login. There is no refresh-token flow on the backend, so a 401 always means
 *   "sign in again".
 */

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
    // Already on the login page: let the form show the error instead of reloading it.
    return;
  }
  window.location.assign('/login');
}

/** UI-friendly error produced from any failure thrown by the API client. */
export interface ApiError {
  status?: number;
  message: string;
  fieldErrors: FieldError[];
}

/** Unknown-credential message reused for login failures (backend wording). */
export const INVALID_CREDENTIALS_MESSAGE = 'Invalid email or password';

/**
 * Translate an Axios failure into something the forms can render:
 * - backend ErrorResponse (validation/409/401) → message + per-field errors,
 * - network/offline failures → a plain "cannot reach the server" message.
 * Internal details (stack, SQL, tokens) are never surfaced.
 */
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
