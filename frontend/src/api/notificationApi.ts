import type { AxiosResponse } from 'axios';
import type { NotificationResponse } from '../types/notification';
import { api } from './client';

/**
 * Notification endpoints ({@code /api/v1/notifications}). Shapes mirror the backend
 * DTOs exactly. No user ID is ever sent — the backend resolves the caller from the JWT.
 */

/** GET /notifications — the caller's own notifications, newest first. */
export async function getMyNotifications(): Promise<NotificationResponse[]> {
  const response: AxiosResponse<NotificationResponse[]> = await api.get('/notifications');
  return response.data;
}

/** GET /notifications/unread-count — the number shown on the bell badge. */
export async function getUnreadNotificationCount(): Promise<number> {
  const response: AxiosResponse<number> = await api.get('/notifications/unread-count');
  return response.data;
}

/** PATCH /notifications/:id/read — mark one own notification read. */
export async function markNotificationRead(id: number): Promise<NotificationResponse> {
  const response: AxiosResponse<NotificationResponse> = await api.patch(`/notifications/${id}/read`);
  return response.data;
}

/** POST /notifications/read-all — mark everything read; resolves to the new count (0). */
export async function markAllNotificationsRead(): Promise<number> {
  const response: AxiosResponse<number> = await api.post('/notifications/read-all');
  return response.data;
}
