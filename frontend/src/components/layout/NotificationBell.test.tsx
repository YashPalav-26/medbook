import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { NotificationBell } from './NotificationBell';
import * as notificationApi from '../../api/notificationApi';
import type { NotificationResponse } from '../../types/notification';

vi.mock('../../api/notificationApi', () => ({
  getMyNotifications: vi.fn(),
  getUnreadNotificationCount: vi.fn(),
  markNotificationRead: vi.fn(),
  markAllNotificationsRead: vi.fn(),
}));

vi.mock('../../hooks/useAuth', () => ({
  useAuth: () => ({
    isAuthenticated: true,
    user: { id: 1, email: 'me@medbook.test', firstName: 'Paul', lastName: 'Patient', roles: ['ROLE_PATIENT'] },
    login: vi.fn(), register: vi.fn(), logout: vi.fn(), updateUser: vi.fn(),
  }),
}));

const mockedApi = vi.mocked(notificationApi);

const NOW = Date.now();

function makeNotification(overrides: Partial<NotificationResponse>): NotificationResponse {
  return {
    id: 1,
    type: 'APPOINTMENT_CREATED',
    title: 'Appointment booked',
    message: 'Your appointment with Dr. Dana Doctor on 2026-10-05 at 10:00 has been booked.',
    referenceId: 51,
    read: false,
    createdAt: new Date(NOW - 2 * 60 * 1000).toISOString(),
    ...overrides,
  };
}

function renderBell(initialPath = '/dashboard') {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[initialPath]}>
        <Routes>
          <Route path="/appointments/:id" element={<div>APPOINTMENT DETAIL PAGE</div>} />
          <Route path="*" element={<NotificationBell />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  mockedApi.getUnreadNotificationCount.mockResolvedValue(0);
  mockedApi.getMyNotifications.mockResolvedValue([]);
  mockedApi.markNotificationRead.mockResolvedValue(makeNotification({ read: true }));
  mockedApi.markAllNotificationsRead.mockResolvedValue(0);
});

describe('NotificationBell — rendering', () => {
  it('renders the bell button and opens the panel on click', async () => {
    const user = userEvent.setup();
    renderBell();

    const bell = screen.getByRole('button', { name: /notifications/i });
    expect(bell).toBeInTheDocument();
    expect(screen.queryByTestId('notification-panel')).not.toBeInTheDocument();

    await user.click(bell);
    expect(await screen.findByTestId('notification-panel')).toBeInTheDocument();
    expect(screen.getByText('Notifications')).toBeInTheDocument();
  });

  it('shows the unread badge with the count from the API', async () => {
    mockedApi.getUnreadNotificationCount.mockResolvedValue(3);
    renderBell();

    const badge = await screen.findByTestId('unread-badge');
    await waitFor(() => expect(badge).toHaveTextContent('3'));
    expect(badge).not.toHaveClass('MuiBadge-invisible');
    expect(screen.getByRole('button', { name: /3 unread/i })).toBeInTheDocument();
  });

  it('shows the empty state when there are no notifications', async () => {
    const user = userEvent.setup();
    renderBell();

    await user.click(screen.getByRole('button', { name: /notifications/i }));
    expect(await screen.findByText('No notifications yet')).toBeInTheDocument();
    expect(screen.queryByTestId('notification-item')).not.toBeInTheDocument();
  });

  it('lists notifications with title, message, type label and timestamp', async () => {
    mockedApi.getMyNotifications.mockResolvedValue([
      makeNotification({}),
      makeNotification({
        id: 2,
        type: 'APPOINTMENT_CONFIRMED',
        title: 'Appointment confirmed',
        read: true,
      }),
    ]);
    const user = userEvent.setup();
    renderBell();

    await user.click(screen.getByRole('button', { name: /notifications/i }));

    expect(await screen.findByText('Appointment booked')).toBeInTheDocument();
    expect(screen.getByText('Appointment confirmed')).toBeInTheDocument();
    expect(screen.getByText('Booked')).toBeInTheDocument();
    expect(screen.getByText('Confirmed')).toBeInTheDocument();
    expect(screen.getAllByText(/2m ago/).length).toBeGreaterThanOrEqual(1);

    expect(screen.getAllByTestId('notification-item')[0]).toHaveAttribute('data-read', 'false');
    expect(screen.getAllByTestId('notification-item')[1]).toHaveAttribute('data-read', 'true');
  });

  it('shows skeletons while the list is loading and an error box on failure', async () => {
    mockedApi.getMyNotifications.mockRejectedValue({
      isAxiosError: true,
      response: { status: 500, data: { message: 'boom' } },
    });
    const user = userEvent.setup();
    renderBell();

    await user.click(screen.getByRole('button', { name: /notifications/i }));

    expect(await screen.findByText('Could not load notifications.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /try again/i })).toBeInTheDocument();
  });
});

describe('NotificationBell — interactions', () => {
  it('marks a notification read and navigates to the appointment detail page', async () => {
    mockedApi.getMyNotifications.mockResolvedValue([makeNotification({ id: 7, referenceId: 4242 })]);
    const user = userEvent.setup();
    renderBell();

    await user.click(screen.getByRole('button', { name: /notifications/i }));
    await user.click(await screen.findByTestId('notification-item'));

    await waitFor(() => expect(mockedApi.markNotificationRead).toHaveBeenCalledWith(7));

    expect(await screen.findByText('APPOINTMENT DETAIL PAGE')).toBeInTheDocument();
    expect(screen.queryByTestId('notification-panel')).not.toBeInTheDocument();
  });

  it('marks all notifications read and the badge disappears', async () => {
    mockedApi.getUnreadNotificationCount.mockResolvedValue(2);
    mockedApi.getMyNotifications.mockResolvedValue([
      makeNotification({ id: 1 }),
      makeNotification({ id: 2, type: 'APPOINTMENT_CANCELLED', title: 'Appointment cancelled', read: true }),
    ]);
    const user = userEvent.setup();
    renderBell();

    const badge = await screen.findByTestId('unread-badge');
    await waitFor(() => expect(badge).toHaveTextContent('2'));

    await user.click(screen.getByRole('button', { name: /notifications/i }));
    await user.click(await screen.findByTestId('mark-all-read'));

    await waitFor(() => expect(mockedApi.markAllNotificationsRead).toHaveBeenCalled());

    await waitFor(() => expect(badge).toHaveClass('MuiBadge-invisible'));

    await waitFor(() => {
      screen.getAllByTestId('notification-item').forEach((item) => {
        expect(item).toHaveAttribute('data-read', 'true');
      });
    });
  });

  it('marks read on click without referenceId and stays on the page', async () => {
    mockedApi.getMyNotifications.mockResolvedValue([makeNotification({ id: 9, referenceId: null })]);
    const user = userEvent.setup();
    renderBell();

    await user.click(screen.getByRole('button', { name: /notifications/i }));
    await user.click(await screen.findByTestId('notification-item'));

    await waitFor(() => expect(mockedApi.markNotificationRead).toHaveBeenCalledWith(9));
    expect(screen.queryByText('APPOINTMENT DETAIL PAGE')).not.toBeInTheDocument();
    expect(screen.queryByTestId('notification-panel')).not.toBeInTheDocument();
  });

  it('closes the panel on outside click (mobile/desktop dismiss behaviour)', async () => {
    const user = userEvent.setup();
    renderBell();

    await user.click(screen.getByRole('button', { name: /notifications/i }));
    expect(await screen.findByTestId('notification-panel')).toBeInTheDocument();

    fireEvent.mouseDown(document.body);
    await waitFor(() =>
      expect(screen.queryByTestId('notification-panel')).not.toBeInTheDocument(),
    );
  });
});
