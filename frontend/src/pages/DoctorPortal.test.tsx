import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Toaster } from 'sonner';
import { AxiosError, AxiosHeaders, type AxiosResponse } from 'axios';
import { DoctorDashboardPage } from './DoctorDashboardPage';
import { DoctorAppointmentsPage } from './DoctorAppointmentsPage';
import { AppointmentDetailPage } from './AppointmentDetailPage';
import * as appointmentApi from '../api/appointmentApi';
import type { AppointmentResponse } from '../types/appointment';

vi.mock('../api/appointmentApi', () => ({
  getMyAppointments: vi.fn(),
  getAppointment: vi.fn(),
  confirmAppointment: vi.fn(),
  completeAppointment: vi.fn(),
  markAppointmentNoShow: vi.fn(),
}));

const mockedGetMyAppointments = vi.mocked(appointmentApi.getMyAppointments);
const mockedGetAppointment = vi.mocked(appointmentApi.getAppointment);
const mockedConfirm = vi.mocked(appointmentApi.confirmAppointment);
const mockedComplete = vi.mocked(appointmentApi.completeAppointment);
const mockedNoShow = vi.mocked(appointmentApi.markAppointmentNoShow);

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
  return { ...actual, useNavigate: () => vi.fn() };
});

let currentRoles: string[] = ['ROLE_DOCTOR'];

vi.mock('../hooks/useAuth', () => ({
  useAuth: () => ({
    user: {
      id: 9001,
      email: 'doctor@medbook.test',
      firstName: 'Dana',
      lastName: 'Doyle',
      roles: currentRoles,
    },
    token: 'fake-token',
    isAuthenticated: true,
    login: vi.fn(),
    register: vi.fn(),
    logout: vi.fn(),
  }),
}));

const appointment: AppointmentResponse = {
  id: 3100,
  patientId: 1350,
  patientName: 'John Carter',
  doctorId: 1,
  doctorName: 'Dana Doyle',
  appointmentDate: '2026-09-28',
  startTime: '09:00',
  endTime: '09:30',
  status: 'SCHEDULED',
  cancellationReason: null,
  consultationFeeSnapshot: 500,
};

function makeAxiosError(status: number): AxiosError {
  const response = {
    status,
    data: { message: `error-${status}`, status },
    headers: {},
    config: { headers: new AxiosHeaders() },
  } as unknown as AxiosResponse;
  return new AxiosError('Request failed', 'ERR_BAD_REQUEST', undefined, null, response);
}

function renderWithProviders(
  ui: React.ReactElement,
  { route = '/' }: { route?: string } = {},
) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[route]}>
        {/* Sonner's Toaster stands in for the one App.tsx mounts. */}
        <Toaster position="bottom-right" />
        <Routes>
          <Route path="*" element={ui} />
          <Route path="/appointments/:id" element={<AppointmentDetailPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function renderDetail(status: AppointmentResponse['status'], roles: string[] = ['ROLE_DOCTOR']) {
  currentRoles = roles;
  mockedGetAppointment.mockResolvedValue({ ...appointment, status });
  return renderWithProviders(<AppointmentDetailPage />, { route: `/appointments/${appointment.id}` });
}

beforeEach(() => {
  vi.clearAllMocks();
  currentRoles = ['ROLE_DOCTOR'];
});

describe('DoctorDashboardPage', () => {
  it('loads the doctor schedule from /appointments/me', async () => {
    mockedGetMyAppointments.mockResolvedValue([appointment]);
    renderWithProviders(<DoctorDashboardPage />);
    await waitFor(() => {
      expect(mockedGetMyAppointments).toHaveBeenCalled();
    });
  });

  it('shows the empty state when the doctor has nothing scheduled', async () => {
    mockedGetMyAppointments.mockResolvedValue([]);
    renderWithProviders(<DoctorDashboardPage />);
    expect(await screen.findByText(/Nothing booked for today/i)).toBeInTheDocument();
  });
});

describe('DoctorAppointmentsPage', () => {
  it('lists doctor appointments with status badges and filter counts', async () => {
    mockedGetMyAppointments.mockResolvedValue([appointment]);
    renderWithProviders(<DoctorAppointmentsPage />);
    expect(await screen.findByText('Appointment #3100')).toBeInTheDocument();
    expect(screen.getByText('John Carter')).toBeInTheDocument();
    expect(screen.getByText('All (1)')).toBeInTheDocument();
  });

  it('shows the empty state when the doctor has no appointments', async () => {
    mockedGetMyAppointments.mockResolvedValue([]);
    renderWithProviders(<DoctorAppointmentsPage />);
    expect(await screen.findByText(/No appointments yet/i)).toBeInTheDocument();
  });

  it('surfaces a 403 as a readable error', async () => {
    mockedGetMyAppointments.mockRejectedValue(makeAxiosError(403));
    renderWithProviders(<DoctorAppointmentsPage />);
    expect(await screen.findByText('You are not allowed to view this list.', {}, { timeout: 4000 })).toBeInTheDocument();
  });
});

describe('AppointmentDetailPage (role-aware doctor actions)', () => {
  it('shows Confirm for a SCHEDULED appointment when signed in as doctor', async () => {
    renderDetail('SCHEDULED');
    expect(await screen.findByRole('button', { name: /Confirm Appointment/i })).toBeInTheDocument();
  });

  it('shows Complete and No-Show for a CONFIRMED appointment when signed in as doctor', async () => {
    renderDetail('CONFIRMED');
    expect(await screen.findByRole('button', { name: /Mark Completed/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Mark No-Show/i })).toBeInTheDocument();
  });

  it('shows no doctor actions for a patient on their own appointment', async () => {
    renderDetail('SCHEDULED', ['ROLE_PATIENT']);
    expect(await screen.findByText('John Carter')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Confirm Appointment/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Mark Completed/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Mark No-Show/i })).not.toBeInTheDocument();
  });

  it('confirms through the dialog and updates the status badge', async () => {
    renderDetail('SCHEDULED');
    mockedConfirm.mockResolvedValue({ ...appointment, status: 'CONFIRMED' });
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: /Confirm Appointment/i }));
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Confirm Appointment' }));
    await waitFor(() => expect(mockedConfirm).toHaveBeenCalledWith(3100));
    expect(await screen.findByText('Confirmed')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Confirm Appointment/i })).not.toBeInTheDocument();
  });

  it('completes a CONFIRMED appointment through the dialog', async () => {
    renderDetail('CONFIRMED');
    mockedComplete.mockResolvedValue({ ...appointment, status: 'COMPLETED' });
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: /Mark Completed/i }));
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Mark Completed' }));
    await waitFor(() => expect(mockedComplete).toHaveBeenCalledWith(3100));
    expect(await screen.findByText('Completed')).toBeInTheDocument();
  });

  it('no-shows a CONFIRMED appointment through the dialog', async () => {
    renderDetail('CONFIRMED');
    mockedNoShow.mockResolvedValue({ ...appointment, status: 'NO_SHOW' });
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: /Mark No-Show/i }));
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Mark No-Show' }));
    await waitFor(() => expect(mockedNoShow).toHaveBeenCalledWith(3100));
    expect(await screen.findByText('No show')).toBeInTheDocument(); // chip label is humanized
  });

  it('surfaces a transition error (409) inside the dialog', async () => {
    renderDetail('SCHEDULED');
    mockedConfirm.mockRejectedValue(makeAxiosError(409));
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: /Confirm Appointment/i }));
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Confirm Appointment' }));
    expect(await screen.findByText('error-409')).toBeInTheDocument();
  });

  it('hides lifecycle buttons for terminal states', async () => {
    renderDetail('COMPLETED');
    expect(await screen.findByText('Completed')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Confirm Appointment/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Mark Completed/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Mark No-Show/i })).not.toBeInTheDocument();
  });
});
