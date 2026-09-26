/**
 * Types mirroring the backend notification DTOs exactly (Step 25).
 * Source of truth: backend/src/main/java/com/medbook/dto/NotificationResponse.java
 */

/** Which appointment lifecycle event produced the notification. */
export type NotificationType =
  | 'APPOINTMENT_CREATED'
  | 'APPOINTMENT_CONFIRMED'
  | 'APPOINTMENT_CANCELLED'
  | 'APPOINTMENT_COMPLETED'
  | 'APPOINTMENT_NO_SHOW';

/** One notification as returned by GET /api/v1/notifications. */
export interface NotificationResponse {
  id: number;
  type: NotificationType;
  title: string;
  message: string;
  /** The related appointment's ID, when the notification is about one. */
  referenceId: number | null;
  read: boolean;
  createdAt: string; // ISO-8601 timestamp
}
