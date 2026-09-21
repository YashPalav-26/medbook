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

  it('renders the loading state first', () => {
    mockedGetMyAppointments.mockReturnValue(new Promise(() => {}));
    renderPage();
    expect(screen.getByText(/loading your appointments/i)).toBeInTheDocument();
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
    expect(screen.getAllByText('Patient ID: 1350')).toHaveLength(2);
    expect(screen.getAllByText('Doctor ID: 1')).toHaveLength(2);
  });

  it('renders the empty state when the patient has no appointments', async () => {
    mockedGetMyAppointments.mockResolvedValueOnce([]);
    renderPage();

    await waitFor(() => {
      expect(screen.getByText('No appointments yet')).toBeInTheDocument();
    });
    expect(screen.getByText(/You have not booked any appointments/i)).toBeInTheDocument();
  });

  it('renders a generic API error message', async () => {
    // Reject EVERY call: the hook retries once, and the retry must fail the same way.
    mockedGetMyAppointments.mockRejectedValue(axiosError(500));
    renderPage();

    await waitFor(
      () => {
        expect(screen.getByText('Error Loading Appointments')).toBeInTheDocument();
      },
      { timeout: 4000 },
    );
    expect(screen.getByRole('button', { name: /back to dashboard/i })).toBeInTheDocument();
  });

  it('renders a forbidden message on 403', async () => {
    mockedGetMyAppointments.mockRejectedValue(axiosError(403));
    renderPage();

    await waitFor(
      () => {
        expect(screen.getByText('Not Allowed')).toBeInTheDocument();
      },
      { timeout: 4000 },
    );
    expect(screen.getByText(/You do not have access to this resource/i)).toBeInTheDocument();
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
