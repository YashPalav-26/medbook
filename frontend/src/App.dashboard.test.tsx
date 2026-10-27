import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import App from './App';

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

  it('renders the redesigned dashboard with welcome, quick actions and shell navigation', () => {

    window.history.pushState({}, '', '/dashboard');
    renderApp();

    expect(screen.getByText(/Good to see you, Test/i)).toBeInTheDocument();

    expect(screen.getAllByText('Book Appointment').length).toBeGreaterThan(0);
    expect(screen.getAllByText('My Appointments').length).toBeGreaterThan(0);
  });

  it('links My Appointments to the appointments list route', () => {
    window.history.pushState({}, '', '/dashboard');
    renderApp();

    const link = screen.getByRole('link', { name: 'My Appointments' });
    expect(link).toHaveAttribute('href', '/appointments/me');
  });

  it('shows the My Appointments page at /appointments/me', async () => {
    window.history.pushState({}, '', '/appointments/me');
    renderApp();

    await screen.findByText('No appointments yet');
    expect(screen.getByRole('heading', { name: /my appointments/i })).toBeInTheDocument();
  });
});
