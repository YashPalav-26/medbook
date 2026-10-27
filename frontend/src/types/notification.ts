

export type NotificationType =
  | 'APPOINTMENT_CREATED'
  | 'APPOINTMENT_CONFIRMED'
  | 'APPOINTMENT_CANCELLED'
  | 'APPOINTMENT_COMPLETED'
  | 'APPOINTMENT_NO_SHOW';

export interface NotificationResponse {
  id: number;
  type: NotificationType;
  title: string;
  message: string;

  referenceId: number | null;
  read: boolean;
  createdAt: string;
}
