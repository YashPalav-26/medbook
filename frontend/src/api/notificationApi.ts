import type { AxiosResponse } from 'axios';
import type { NotificationResponse } from '../types/notification';
import { api } from './client';

export async function getMyNotifications(): Promise<NotificationResponse[]> {
  const response: AxiosResponse<NotificationResponse[]> = await api.get('/notifications');
  return response.data;
}

export async function getUnreadNotificationCount(): Promise<number> {
  const response: AxiosResponse<number> = await api.get('/notifications/unread-count');
  return response.data;
}

export async function markNotificationRead(id: number): Promise<NotificationResponse> {
  const response: AxiosResponse<NotificationResponse> = await api.patch(`/notifications/${id}/read`);
  return response.data;
}

export async function markAllNotificationsRead(): Promise<number> {
  const response: AxiosResponse<number> = await api.post('/notifications/read-all');
  return response.data;
}
