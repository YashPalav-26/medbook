import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import IconButton from '@mui/material/IconButton';
import Badge from '@mui/material/Badge';
import {
  Bell, CalendarPlus, CalendarCheck, CalendarX2, CalendarClock, CalendarX,
  Inbox, CheckCheck,
} from 'lucide-react';
import { useNotifications, useUnreadNotificationCount, useMarkNotificationRead, useMarkAllNotificationsRead } from '../../hooks/useNotifications';
import { ErrorBox, Skeleton } from '../ui/Feedback';
import type { NotificationType } from '../../types/notification';













const TYPE_STYLES: Record<NotificationType, { icon: typeof Bell; classes: string }> = {
  APPOINTMENT_CREATED: { icon: CalendarPlus, classes: 'bg-info-100 text-info-700' },
  APPOINTMENT_CONFIRMED: { icon: CalendarCheck, classes: 'bg-brand-100 text-brand-700' },
  APPOINTMENT_CANCELLED: { icon: CalendarX2, classes: 'bg-danger-100 text-danger-700' },
  APPOINTMENT_COMPLETED: { icon: CalendarClock, classes: 'bg-ink-100 text-ink-700' },
  APPOINTMENT_NO_SHOW: { icon: CalendarX, classes: 'bg-warning-100 text-warning-700' },
};

const TYPE_LABELS: Record<NotificationType, string> = {
  APPOINTMENT_CREATED: 'Booked',
  APPOINTMENT_CONFIRMED: 'Confirmed',
  APPOINTMENT_CANCELLED: 'Cancelled',
  APPOINTMENT_COMPLETED: 'Completed',
  APPOINTMENT_NO_SHOW: 'No-show',
};


function timeAgo(iso: string): string {
  const then = new Date(iso).getTime();
  if (Number.isNaN(then)) return '';
  const seconds = Math.max(0, Math.floor((Date.now() - then) / 1000));
  if (seconds < 60) return 'just now';
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  if (days < 7) return `${days}d ago`;
  return new Date(iso).toLocaleDateString();
}

export function NotificationBell() {
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const panelRef = useRef<HTMLDivElement>(null);
  const bellRef = useRef<HTMLButtonElement>(null);
  const wasOpen = useRef(false);
  const navigate = useNavigate();

  const { data: unreadCount = 0 } = useUnreadNotificationCount();
  const { data: notifications = [], isLoading, isError, refetch } = useNotifications(open);
  const markRead = useMarkNotificationRead();
  const markAllRead = useMarkAllNotificationsRead();



  useEffect(() => {
    if (open) {
      panelRef.current?.focus();
    } else if (wasOpen.current) {
      bellRef.current?.focus();
    }
    wasOpen.current = open;
  }, [open]);


  useEffect(() => {
    if (!open) return;
    const onPointerDown = (event: MouseEvent | TouchEvent) => {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setOpen(false);
      }
    };
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false);
    };
    document.addEventListener('mousedown', onPointerDown);
    document.addEventListener('touchstart', onPointerDown);
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('mousedown', onPointerDown);
      document.removeEventListener('touchstart', onPointerDown);
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [open]);

  const handleItemClick = (id: number, referenceId: number | null, read: boolean) => {
    if (!read) markRead.mutate(id);
    setOpen(false);
    if (referenceId != null) navigate(`/appointments/${referenceId}`);
  };

  return (
    <div className="relative" ref={containerRef}>
      {/* MUI IconButton + Badge — the unread count is a standard badge primitive. */}
      <IconButton
        ref={bellRef}
        onClick={() => setOpen((value) => !value)}
        aria-label={`Notifications${unreadCount > 0 ? ` (${unreadCount} unread)` : ''}`}
        aria-expanded={open}
        aria-haspopup="true"
        aria-controls={open ? 'notifications-panel' : undefined}
        data-testid="notification-bell"
        size="small"
        sx={{ borderRadius: 2, color: 'text.secondary' }}
      >
        <Badge
          badgeContent={unreadCount > 99 ? '99+' : unreadCount}
          color="error"
          slotProps={


            { badge: { 'data-testid': 'unread-badge' } } as object
          }
        >
          <Bell className="h-5 w-5" aria-hidden="true" />
        </Badge>
      </IconButton>

      {open && (
        <div
          ref={panelRef}
          id="notifications-panel"
          tabIndex={-1}
          data-testid="notification-panel"
          className="absolute right-0 z-40 mt-2 w-80 max-w-[calc(100vw-2rem)] overflow-hidden rounded-card border border-ink-100 bg-white shadow-modal focus:outline-none
            max-sm:fixed max-sm:inset-x-3 max-sm:top-16 max-sm:mt-0 max-sm:w-auto"
          role="dialog"
          aria-label="Notifications"
        >
          <div className="flex items-center justify-between border-b border-ink-100 px-4 py-3">
            <p className="font-display text-sm font-semibold text-ink-900">Notifications</p>
            {unreadCount > 0 && (
              <button
                type="button"
                onClick={() => markAllRead.mutate()}
                disabled={markAllRead.isPending}
                data-testid="mark-all-read"
                className="flex items-center gap-1 text-xs font-medium text-brand-700 hover:text-brand-800 disabled:opacity-60"
              >
                <CheckCheck className="h-3.5 w-3.5" aria-hidden="true" />
                Mark all read
              </button>
            )}
          </div>

          <div className="max-h-96 overflow-y-auto">
            {isLoading ? (
              <div className="space-y-3 p-4">
                <Skeleton className="h-12 w-full" />
                <Skeleton className="h-12 w-full" />
                <Skeleton className="h-12 w-3/4" />
              </div>
            ) : isError ? (
              <div className="p-4">
                <ErrorBox message="Could not load notifications." onRetry={() => refetch()} />
              </div>
            ) : notifications.length === 0 ? (
              <div className="flex flex-col items-center gap-2 px-4 py-10 text-center">
                <Inbox className="h-8 w-8 text-ink-300" aria-hidden="true" />
                <p className="text-sm font-medium text-ink-700">No notifications yet</p>
                <p className="text-xs text-ink-500">Updates about your appointments will appear here.</p>
              </div>
            ) : (
              <ul>
                {notifications.map((notification) => {
                  const style = TYPE_STYLES[notification.type];
                  const Icon = style.icon;
                  return (
                    <li key={notification.id} className="border-b border-ink-50 last:border-0">
                      <button
                        type="button"
                        onClick={() => handleItemClick(notification.id, notification.referenceId, notification.read)}
                        disabled={markRead.isPending && markRead.variables === notification.id}
                        data-testid="notification-item"
                        data-read={notification.read}
                        className={`flex w-full items-start gap-3 px-4 py-3 text-left transition-colors hover:bg-ink-50 ${
                          notification.read ? 'bg-white' : 'bg-brand-50/60'
                        }`}
                      >
                        <span
                          className={`mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-full ${style.classes}`}
                        >
                          <Icon className="h-4 w-4" aria-hidden="true" />
                        </span>
                        <span className="min-w-0 flex-1">
                          <span className="flex items-center justify-between gap-2">
                            <span
                              className={`truncate text-sm ${
                                notification.read ? 'font-normal text-ink-600' : 'font-semibold text-ink-900'
                              }`}
                            >
                              {notification.title}
                            </span>
                            {!notification.read && (
                              <span className="h-2 w-2 shrink-0 rounded-full bg-brand-600" aria-label="unread" />
                            )}
                          </span>
                          <span className="mt-0.5 line-clamp-2 block text-xs text-ink-500">
                            {notification.message}
                          </span>
                          <span className="mt-1 flex items-center gap-2">
                            <span
                              className={`inline-flex items-center rounded-full px-2 py-0.5 text-[10px] font-medium ${style.classes}`}
                            >
                              {TYPE_LABELS[notification.type]}
                            </span>
                            <span className="text-[10px] text-ink-400">{timeAgo(notification.createdAt)}</span>
                          </span>
                        </span>
                      </button>
                    </li>
                  );
                })}
              </ul>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
