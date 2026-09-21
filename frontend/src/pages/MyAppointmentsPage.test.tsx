import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
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
  doctorId: 1,
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
  doctorId: 1,
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
        <MyAppointmentsPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

/** Build an AxiosError shaped like the ones the API client produces. */
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
    // Skeletons are aria-hidden decorative divs; there must be several, and no list yet.
    const skeletons = document.querySelectorAll('.animate-pulse');
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
    expect(screen.getByText('SCHEDULED')).toBeInTheDocument();
    expect(screen.getByText('CANCELLED')).toBeInTheDocument();
    expect(screen.getByText(/Plan changed/)).toBeInTheDocument();
    // Doctor/patient context line on each card (real API fields).
    expect(screen.getAllByText(/Doctor #1 · Patient #1350/)).toHaveLength(2);
    // Fee is rendered with two decimals on both cards.
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
    // Reject EVERY call: the hook retries once, and the retry must fail the same way.
    mockedGetMyAppointments.mockRejectedValue(axiosError(500));
    renderPage();

    await waitFor(
      () => {
        // The error callout (role=alert) shows the API error, never a raw dump.
        expect(screen.getByRole('alert')).toBeInTheDocument();
      },
      { timeout: 4000 },
    );
    // Errors are actionable: a retry control must exist.
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

  it('navigates to the appointment detail page when a card is clicked', async () => {
    const user = userEvent.setup();
    mockedGetMyAppointments.mockResolvedValueOnce([appointmentA]);
    renderPage();

    await waitFor(() => {
      expect(screen.getByText('Appointment #1101')).toBeInTheDocument();
    });
    await user.click(screen.getByText('Appointment #1101'));

    expect(navigateMock).toHaveBeenCalledWith('/appointments/1101');
  });
});
