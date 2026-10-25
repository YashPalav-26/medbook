import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Toaster } from 'sonner';
import { AxiosError, AxiosHeaders, type AxiosResponse } from 'axios';
import { MyAppointmentsPage } from './MyAppointmentsPage';
import * as appointmentApi from '../api/appointmentApi';
import type { AppointmentResponse } from '../types/appointment';

vi.mock('../api/appointmentApi', () => ({
  getMyAppointments: vi.fn(),
}));

const navigateMock = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
  return { ...actual, useNavigate: () => navigateMock };
});

vi.mock('../hooks/useAuth', () => ({
  useAuth: () => ({
    user: { id: 42, email: 'me@medbook.test', firstName: 'Test', lastName: 'User', roles: ['ROLE_PATIENT'] },
    token: 'fake-token',
    isAuthenticated: true,
    login: vi.fn(),
    register: vi.fn(),
    logout: vi.fn(),
  }),
}));

const mockedGetMyAppointments = vi.mocked(appointmentApi.getMyAppointments);

const appointmentA: AppointmentResponse = {
  id: 1101,
  patientId: 1350,
  patientName: 'John Carter',
  doctorId: 1,
  doctorName: 'Sarah Wells',
  appointmentDate: '2026-09-28',
  startTime: '09:00',
  endTime: '09:30',
  status: 'SCHEDULED',
  cancellationReason: null,
  consultationFeeSnapshot: 500,
};

const appointmentB: AppointmentResponse = {
  id: 1102,
  patientId: 1350,
  patientName: 'John Carter',
  doctorId: 1,
  doctorName: 'Sarah Wells',
  appointmentDate: '2026-09-29',
  startTime: '10:00',
  endTime: '10:30',
  status: 'CANCELLED',
  cancellationReason: 'Plan changed',
  consultationFeeSnapshot: 500,
};

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        {/* Sonner's Toaster stands in for the one App.tsx mounts, so success
            toasts render inside the test DOM like they do in the browser. */}
        <Toaster position="bottom-right" />
        <MyAppointmentsPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}


function axiosError(status: number): AxiosError {
  const response: AxiosResponse = {
    status,
    statusText: String(status),
    headers: {},
    config: { headers: new AxiosHeaders() },
    data: { message: status === 401 ? 'Authentication is required' : 'You do not have access to this resource' },
  };
  return new AxiosError('Request failed', 'ERR_BAD_REQUEST', undefined, undefined, response);
}

describe('MyAppointmentsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders skeleton placeholders while loading (never a bare "Loading...")', () => {
    mockedGetMyAppointments.mockReturnValue(new Promise(() => {}));
    renderPage();

    const skeletons = document.querySelectorAll('.MuiSkeleton-root');
    expect(skeletons.length).toBeGreaterThanOrEqual(3);
    expect(screen.queryByText('Appointment #1101')).not.toBeInTheDocument();
  });

  it('renders the appointment list from the real API response', async () => {
    mockedGetMyAppointments.mockResolvedValueOnce([appointmentA, appointmentB]);
    renderPage();

    await waitFor(() => {
      expect(screen.getByText('Appointment #1101')).toBeInTheDocument();
    });
    expect(screen.getByText('Appointment #1102')).toBeInTheDocument();

    const chipLabels = Array.from(document.querySelectorAll('.MuiChip-label')).map((el) => el.textContent);
    expect(chipLabels).toContain('Scheduled');
    expect(chipLabels).toContain('Cancelled');
    expect(screen.getByText(/Plan changed/)).toBeInTheDocument();

    expect(screen.getAllByText('Dr. Sarah Wells')).toHaveLength(2);

    expect(screen.getAllByText('$500.00')).toHaveLength(2);
  });

  it('renders the empty state when the patient has no appointments', async () => {
    mockedGetMyAppointments.mockResolvedValueOnce([]);
    renderPage();

    await waitFor(() => {
      expect(screen.getByText('No appointments yet')).toBeInTheDocument();
    });
    expect(screen.getByText(/When you book a visit it will appear here/i)).toBeInTheDocument();
  });

  it('renders a generic API error message', async () => {

    mockedGetMyAppointments.mockRejectedValue(axiosError(500));
    renderPage();

    await waitFor(
      () => {

        expect(screen.getByRole('alert')).toBeInTheDocument();
      },
      { timeout: 4000 },
    );

    expect(screen.getByRole('button', { name: /try again/i })).toBeInTheDocument();
  });

  it('renders a forbidden message on 403', async () => {
    mockedGetMyAppointments.mockRejectedValue(axiosError(403));
    renderPage();

    await waitFor(
      () => {
        expect(screen.getByText('You are not allowed to view this list.')).toBeInTheDocument();
      },
      { timeout: 4000 },
    );
    expect(screen.getByRole('button', { name: /try again/i })).toBeInTheDocument();
  });

  it('moves between status tabs with the arrow keys', async () => {
    const user = userEvent.setup();
    mockedGetMyAppointments.mockResolvedValueOnce([appointmentA]);
    renderPage();

    const allTab = await screen.findByRole('tab', { name: /All/ });
    allTab.focus();
    await user.keyboard('{ArrowRight}');

    expect(screen.getByRole('tab', { name: /Upcoming/ })).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByRole('tab', { name: /Upcoming/ })).toHaveFocus();
  });

  it('links each card to its appointment detail page', async () => {
    mockedGetMyAppointments.mockResolvedValueOnce([appointmentA]);
    renderPage();

    const link = await screen.findByRole('link', { name: /Appointment #1101/i });


    expect(link).toHaveAttribute('href', '/appointments/1101');
    expect(navigateMock).not.toHaveBeenCalled();
  });
});
