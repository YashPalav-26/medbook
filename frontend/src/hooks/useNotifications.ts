import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import * as notificationApi from '../api/notificationApi';
import { useAuth } from './useAuth';

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
