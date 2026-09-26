import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import * as notificationApi from '../api/notificationApi';
import { useAuth } from './useAuth';

/**
 * React Query hooks for the notification bell (Step 25).
 *
 * Query family: ['notifications', ...].
 *
 * Polling: the unread count refetches every 30 seconds while the tab is visible
 * (refetchIntervalInBackground defaults to false, so a hidden tab does not poll).
 * That is deliberately relaxed — notifications are not time-critical, and 30s
 * keeps request volume negligible. Opening the bell always fetches the fresh
 * list (staleTime 0), so what the user sees is never older than the last visit.
 */

/** The authenticated caller's notifications, newest first. Fetched when the panel opens. */
export function useNotifications(open: boolean) {
  const { isAuthenticated } = useAuth();

  return useQuery({
    queryKey: ['notifications', 'list'],
    queryFn: notificationApi.getMyNotifications,
    enabled: isAuthenticated && open,
    staleTime: 0, // always fresh when the bell is opened
    refetchOnWindowFocus: false,
  });
}

/** Unread count for the bell badge; polls every 30s while signed in. */
export function useUnreadNotificationCount() {
  const { isAuthenticated } = useAuth();

  return useQuery({
    queryKey: ['notifications', 'unread-count'],
    queryFn: notificationApi.getUnreadNotificationCount,
    enabled: isAuthenticated,
    staleTime: 1000 * 15,
    refetchInterval: 1000 * 30,
    refetchOnWindowFocus: false,
  });
}

/**
 * Mark one notification read. The notification cache is updated in place (no
 * refetch needed) and the unread count is decremented/recalculated.
 */
export function useMarkNotificationRead() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: number) => notificationApi.markNotificationRead(id),
    onSuccess: (updated) => {
      queryClient.setQueryData<NotificationListItem[]>(['notifications', 'list'], (old) =>
        old
          ? old.map((notification) =>
              notification.id === updated.id ? { ...notification, read: updated.read } : notification,
            )
          : old,
      );
      queryClient.invalidateQueries({ queryKey: ['notifications', 'unread-count'] });
    },
  });
}

type NotificationListItem = Awaited<ReturnType<typeof notificationApi.getMyNotifications>>[number];

/**
 * Mark every notification read. Both caches are updated immediately so the badge
 * disappears and rows restyle without a refetch.
 */
export function useMarkAllNotificationsRead() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: notificationApi.markAllNotificationsRead,
    onSuccess: () => {
      queryClient.setQueryData<NotificationListItem[]>(['notifications', 'list'], (old) =>
        old ? old.map((notification) => ({ ...notification, read: true })) : old,
      );
      queryClient.setQueryData<number>(['notifications', 'unread-count'], 0);
    },
  });
}
