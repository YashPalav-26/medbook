import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import App from './App';

/**
 * Dashboard navigation tests: the "My Appointments" card must lead to the real
 * appointment list route (Step 17), not sit as a dead placeholder.
 *
 * App renders its own <BrowserRouter>, so tests drive the real history with
 * window.history.pushState instead of wrapping another Router.
 */

vi.mock('./hooks/useAuth', () => ({
  useAuth: () => ({
    user: { id: 42, email: 'me@medbook.test', firstName: 'Test', lastName: 'User', roles: ['ROLE_PATIENT'] },
    token: 'fake-token',
    isAuthenticated: true,
    login: vi.fn(),
    register: vi.fn(),
    logout: vi.fn(),
  }),
}));

vi.mock('./api/appointmentApi', () => ({
  getMyAppointments: vi.fn().mockResolvedValue([]),
  getAppointment: vi.fn(),
  createAppointment: vi.fn(),
  cancelAppointment: vi.fn(),
}));

function renderApp() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <App />
    </QueryClientProvider>,
  );
}

describe('Dashboard navigation', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    window.history.pushState({}, '', '/');
  });

  afterEach(() => {
    window.history.pushState({}, '', '/');
  });

  it('renders the dashboard with all three action cards', () => {
    renderApp(); // "/" redirects to /dashboard

    expect(screen.getByRole('heading', { name: 'MedBook', level: 1 })).toBeInTheDocument();
    expect(screen.getByText('Book Appointment')).toBeInTheDocument();
    expect(screen.getByText('My Appointments')).toBeInTheDocument();
    expect(screen.getByText('My Profile')).toBeInTheDocument();
  });

  it('links My Appointments to the appointments list route', () => {
    renderApp();

    const link = screen.getByText('My Appointments').closest('a');
    expect(link).not.toBeNull();
    expect(link).toHaveAttribute('href', '/appointments/me');
  });

  it('shows the My Appointments page at /appointments/me', async () => {
    window.history.pushState({}, '', '/appointments/me');
    renderApp();

    await screen.findByText('No appointments yet');
    expect(screen.getByRole('heading', { name: /my appointments/i })).toBeInTheDocument();
  });
});
