import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BookAppointmentPage } from './BookAppointmentPage';
import * as appointmentApi from '../api/appointmentApi';

// Mock the appointment API so the test never touches the network.
vi.mock('../api/appointmentApi', () => ({
  createAppointment: vi.fn(),
}));

// Mock auth: signed-in patient whose auth user id (999) must NOT leak into the form.
vi.mock('../hooks/useAuth', () => ({
  useAuth: () => ({
    user: { id: 999, email: 'test@example.com', firstName: 'Test', lastName: 'User', roles: ['ROLE_PATIENT'] },
    token: 'fake-token',
    isAuthenticated: true,
    login: vi.fn(),
    register: vi.fn(),
    logout: vi.fn(),
  }),
}));

// Mock navigation so we can assert the redirect target.
const navigateMock = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
  return { ...actual, useNavigate: () => navigateMock };
});

const mockedCreate = vi.mocked(appointmentApi.createAppointment);

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <BookAppointmentPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

const successResponse = {
  id: 42,
  patientId: 1350,
  doctorId: 1,
  appointmentDate: '2026-09-28',
  startTime: '09:00',
  endTime: '09:30',
  status: 'SCHEDULED' as const,
  cancellationReason: null,
  consultationFeeSnapshot: 500,
};

describe('BookAppointmentPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('submits exactly the patient/doctor IDs typed into the form (no silent substitution)', async () => {
    const user = userEvent.setup();
    mockedCreate.mockResolvedValueOnce(successResponse);
    renderPage();

    // Type explicit test data — auth user id 999 must never be submitted instead.
    await user.type(screen.getByLabelText(/patient id/i), '1350');
    await user.type(screen.getByLabelText(/doctor id/i), '1');
    await user.type(screen.getByLabelText(/appointment date/i), '2026-09-28');
    await user.type(screen.getByLabelText(/start time/i), '09:00');
    await user.type(screen.getByLabelText(/end time/i), '09:30');
    await user.click(screen.getByRole('button', { name: /book appointment/i }));

    await waitFor(() => {
      expect(mockedCreate).toHaveBeenCalledTimes(1);
    });
    expect(mockedCreate).toHaveBeenCalledWith({
      patientId: 1350,
      doctorId: 1,
      appointmentDate: '2026-09-28',
      startTime: '09:00',
      endTime: '09:30',
    });
    await waitFor(() => {
      expect(navigateMock).toHaveBeenCalledWith('/appointments/42');
    });
  });

  it('rejects empty patient/doctor ID instead of inventing values', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole('button', { name: /book appointment/i }));

    await waitFor(() => {
      expect(screen.getByText('Patient ID is required')).toBeInTheDocument();
    });
    expect(screen.getByText('Doctor ID is required')).toBeInTheDocument();
    expect(mockedCreate).not.toHaveBeenCalled();
  });

  it('rejects end time earlier than start time', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.type(screen.getByLabelText(/patient id/i), '1350');
    await user.type(screen.getByLabelText(/doctor id/i), '1');
    await user.type(screen.getByLabelText(/appointment date/i), '2026-09-28');
    await user.type(screen.getByLabelText(/start time/i), '10:00');
    await user.type(screen.getByLabelText(/end time/i), '09:00');
    await user.click(screen.getByRole('button', { name: /book appointment/i }));

    await waitFor(() => {
      expect(screen.getByText('End time must be after start time')).toBeInTheDocument();
    });
    expect(mockedCreate).not.toHaveBeenCalled();
  });
});
