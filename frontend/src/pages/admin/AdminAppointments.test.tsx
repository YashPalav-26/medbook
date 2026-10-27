import { render, screen, within, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Toaster } from 'sonner';
import { AdminAppointmentsPage } from './AdminAppointmentsPage';
import { AdminAppointmentDetailPage } from './AdminAppointmentDetailPage';
import { AdminDashboardPage } from './AdminDashboardPage';
import * as adminApi from '../../api/adminApi';
import * as appointmentApi from '../../api/appointmentApi';
import { pickFromSelect } from '../../test/mui';
import { formatDate } from '../../lib/dates';
import type { AdminAppointmentResponse, AdminAppointmentDetailResponse } from '../../types/appointment';

vi.mock('../../api/adminApi', () => ({
  listAdminSpecialties: vi.fn(),
  createSpecialty: vi.fn(),
  updateSpecialty: vi.fn(),
  deactivateSpecialty: vi.fn(),
  listAdminDoctors: vi.fn(),
  getAdminDoctor: vi.fn(),
  createDoctor: vi.fn(),
  updateDoctor: vi.fn(),
  deactivateDoctor: vi.fn(),
  listAdminPatients: vi.fn(),
  listAdminAvailability: vi.fn(),
  createAvailability: vi.fn(),
  updateAvailability: vi.fn(),
  deactivateAvailability: vi.fn(),
  listAdminAppointments: vi.fn(),
  getAdminAppointment: vi.fn(),
  getAdminOverview: vi.fn(),
  cancelAppointmentAsAdmin: vi.fn(),
}));

vi.mock('../../hooks/useAuth', () => ({
  useAuth: () => ({
    isAuthenticated: true,
    user: { id: 1, roles: ['ROLE_ADMIN'] },
    login: vi.fn(), register: vi.fn(), logout: vi.fn(), updateUser: vi.fn(),
  }),
}));

const mockedApi = vi.mocked(adminApi);

vi.mock('../../api/appointmentApi', () => ({
  getSpecialties: vi.fn(),
  getDoctors: vi.fn(),
  getDoctorAvailability: vi.fn(),
  createAppointmentForPatient: vi.fn(),
  getMyAppointments: vi.fn(),
}));
const mockedAppointmentApi = vi.mocked(appointmentApi);

const APPOINTMENTS: AdminAppointmentResponse[] = [
  {
    id: 101,
    patientId: 11,
    patientName: 'John Reed',
    doctorId: 21,
    doctorName: 'Sarah Wells',
    specialty: 'Cardiology',
    appointmentDate: '2026-09-28',
    startTime: '10:00',
    endTime: '10:30',
    status: 'SCHEDULED',
    cancellationReason: null,
    consultationFeeSnapshot: 300,
  },
  {
    id: 102,
    patientId: 12,
    patientName: 'Mary Sue',
    doctorId: 22,
    doctorName: 'Marcus Chen',
    specialty: 'Dermatology',
    appointmentDate: '2026-09-29',
    startTime: '11:00',
    endTime: '11:30',
    status: 'CANCELLED',
    cancellationReason: 'Schedule conflict at work',
    consultationFeeSnapshot: 275,
  },
];

const DETAIL: AdminAppointmentDetailResponse = {
  appointment: APPOINTMENTS[0],
  history: [
    {
      id: 1,
      status: 'SCHEDULED',
      notes: 'Appointment created',
      changedByName: 'John Reed',
      changedAt: '2026-09-20T10:00:00',
    },
    {
      id: 2,
      status: 'CONFIRMED',
      notes: 'Appointment confirmed by doctor',
      changedByName: 'Dr Sarah Wells',
      changedAt: '2026-09-21T09:30:00',
    },
  ],
};

function renderWithRoutes(ui: React.ReactElement, initialPath: string) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[initialPath]}>
          {/* Sonner's Toaster stands in for the one App.tsx mounts. */}
          <Toaster position="bottom-right" />
          <Routes>
            <Route path="/admin/appointments" element={ui} />
            <Route path="/admin/appointments/:id" element={ui} />
            <Route path="/admin" element={ui} />
            <Route path="*" element={<div>NOT FOUND</div>} />
          </Routes>
        </MemoryRouter>
    </QueryClientProvider>,
  );
}

function renderListThenNavigate(ui: React.ReactElement, listPath: string) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[listPath]}>
        <Routes>
          <Route path="/admin/appointments" element={ui} />
          <Route path="/admin/appointments/:id" element={<div>DETAIL PLACEHOLDER</div>} />
          <Route path="*" element={<div>NOT FOUND</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function tableBody() {
  return screen.getByText('ID').closest('table')!.querySelector('tbody')!;
}

beforeEach(() => {
  vi.clearAllMocks();
  mockedApi.listAdminAppointments.mockResolvedValue(APPOINTMENTS);
  mockedAppointmentApi.getSpecialties.mockResolvedValue([
    { id: 10, name: 'Cardiology', description: null, active: true },
  ]);
  mockedAppointmentApi.getDoctors.mockResolvedValue([
    {
      id: 21, firstName: 'Sarah', lastName: 'Wells', specialty: 'Cardiology',
      yearsExperience: 7, bio: null, consultationFee: 300, profilePictureUrl: null,
    },
  ]);
  mockedAppointmentApi.getDoctorAvailability.mockResolvedValue([]);
  mockedAppointmentApi.createAppointmentForPatient.mockResolvedValue({
    id: 9001,
    patientId: 11,
    patientName: 'John Reed',
    doctorId: 21,
    doctorName: 'Sarah Wells',
    appointmentDate: '2026-09-28',
    startTime: '10:00',
    endTime: '10:30',
    status: 'SCHEDULED',
    cancellationReason: null,
    consultationFeeSnapshot: 300,
  });
  mockedApi.listAdminDoctors.mockResolvedValue([
    {
      id: 21, email: 'wells@medbook.test', firstName: 'Sarah', lastName: 'Wells',
      phoneNumber: '+15550001111', specialtyId: 10, specialty: 'Cardiology',
      medicalLicenseNumber: 'LIC-21', yearsExperience: 7, bio: null,
      consultationFee: 300, profilePictureUrl: null, active: true, userActive: true,
    },
    {
      id: 22, email: 'chen@medbook.test', firstName: 'Marcus', lastName: 'Chen',
      phoneNumber: '+15550001112', specialtyId: 10, specialty: 'Cardiology',
      medicalLicenseNumber: 'LIC-22', yearsExperience: 9, bio: null,
      consultationFee: 275, profilePictureUrl: null, active: true, userActive: true,
    },
  ]);
  mockedApi.getAdminAppointment.mockResolvedValue(DETAIL);
  mockedApi.getAdminOverview.mockResolvedValue({
    todayAppointments: 2,
    today: '2026-09-25',
    statusCounts: { SCHEDULED: 3, CONFIRMED: 1, COMPLETED: 2, CANCELLED: 1, NO_SHOW: 0 },
    totalAppointments: 7,
  });
});

describe('AdminAppointmentsPage', () => {
  it('renders the loaded appointments in the table', async () => {
    renderWithRoutes(<AdminAppointmentsPage />, '/admin/appointments');

    expect(await screen.findByText('John Reed')).toBeInTheDocument();

    const body = tableBody();
    expect(within(body).getByText('Mary Sue')).toBeInTheDocument();
    expect(within(body).getByText('Dr. Sarah Wells')).toBeInTheDocument();
    expect(within(body).getAllByText('Cardiology')).toHaveLength(1);
    expect(within(body).getByText(formatDate('2026-09-28'))).toBeInTheDocument();

    expect(within(body).getAllByText('Scheduled')).toHaveLength(1);
    expect(within(body).getAllByText('Cancelled')).toHaveLength(1);
  });

  it('shows a loading skeleton first', async () => {
    let resolveList: (value: AdminAppointmentResponse[]) => void = () => {};
    mockedApi.listAdminAppointments.mockReturnValue(
      new Promise((resolve) => { resolveList = resolve; }),
    );

    renderWithRoutes(<AdminAppointmentsPage />, '/admin/appointments');
    expect(screen.queryByText('John Reed')).not.toBeInTheDocument();

    resolveList(APPOINTMENTS);
    expect(await screen.findByText('John Reed')).toBeInTheDocument();
  });

  it('shows the empty state when there are no appointments', async () => {
    mockedApi.listAdminAppointments.mockResolvedValue([]);
    renderWithRoutes(<AdminAppointmentsPage />, '/admin/appointments');

    expect(await screen.findByText('No appointments yet')).toBeInTheDocument();
  });

  it('sends the date filter to the backend', async () => {
    renderWithRoutes(<AdminAppointmentsPage />, '/admin/appointments');
    await screen.findByText('John Reed');

    fireEvent.change(screen.getByLabelText('Filter by date'), {
      target: { value: '2026-09-28' },
    });

    await waitFor(() =>
      expect(mockedApi.listAdminAppointments).toHaveBeenCalledWith(
        expect.objectContaining({ date: '2026-09-28' }),
      ),
    );
  });

  it('sends the status filter to the backend', async () => {
    const user = userEvent.setup();
    renderWithRoutes(<AdminAppointmentsPage />, '/admin/appointments');
    await screen.findByText('John Reed');

    await pickFromSelect(user, screen, 'Filter by status', 'CONFIRMED');

    await waitFor(() =>
      expect(mockedApi.listAdminAppointments).toHaveBeenCalledWith(
        expect.objectContaining({ status: 'CONFIRMED' }),
      ),
    );
  });

  it('sends the doctor filter to the backend', async () => {
    const user = userEvent.setup();
    renderWithRoutes(<AdminAppointmentsPage />, '/admin/appointments');
    await screen.findByText('John Reed');

    await pickFromSelect(user, screen, 'Filter by doctor', /Dr\. Sarah Wells/);

    await waitFor(() =>
      expect(mockedApi.listAdminAppointments).toHaveBeenCalledWith(
        expect.objectContaining({ doctorId: 21 }),
      ),
    );
  });

  it('clears all filters with the reset button', async () => {
    const user = userEvent.setup();
    renderWithRoutes(<AdminAppointmentsPage />, '/admin/appointments');
    await screen.findByText('John Reed');

    fireEvent.change(screen.getByLabelText('Filter by date'), {
      target: { value: '2026-09-28' },
    });
    await waitFor(() =>
      expect(mockedApi.listAdminAppointments).toHaveBeenCalledWith(
        expect.objectContaining({ date: '2026-09-28' }),
      ),
    );

    const clearButton = screen.getByRole('button', { name: /clear filters/i });
    expect(clearButton).toBeEnabled();
    await user.click(clearButton);

    await waitFor(() =>
      expect(mockedApi.listAdminAppointments).toHaveBeenLastCalledWith({}),
    );
    expect(screen.getByLabelText('Filter by date')).toHaveValue('');
  });

  it('navigates to the admin detail page from a row', async () => {
    const user = userEvent.setup();
    renderListThenNavigate(<AdminAppointmentsPage />, '/admin/appointments');

    await user.click(await screen.findByRole('link', { name: /view appointment #101/i }));

    expect(await screen.findByText('DETAIL PLACEHOLDER')).toBeInTheDocument();
    expect(mockedApi.listAdminAppointments).toHaveBeenCalled();
  });

  it('renders a 403 error with the friendly message', async () => {
    mockedApi.listAdminAppointments.mockRejectedValue({
      isAxiosError: true,
      response: { status: 403, data: { message: 'Access Denied' } },
    });
    renderWithRoutes(<AdminAppointmentsPage />, '/admin/appointments');

    expect(
      await screen.findByText('You are not allowed to view clinic appointments.'),
    ).toBeInTheDocument();
  });

  it('renders other API errors with the backend message and a retry', async () => {
    mockedApi.listAdminAppointments.mockRejectedValue({
      isAxiosError: true,
      response: { status: 500, data: { message: 'Internal error' } },
    });
    renderWithRoutes(<AdminAppointmentsPage />, '/admin/appointments');

    expect(await screen.findByText('Internal error')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /try again/i })).toBeInTheDocument();
  });
});

describe('AdminAppointmentDetailPage', () => {
  it('renders the appointment summary with names, specialty, fee and cancellation info', async () => {
    renderWithRoutes(
      <AdminAppointmentDetailPage />,
      '/admin/appointments/101',
    );

    expect(await screen.findByText('Appointment #101')).toBeInTheDocument();

    expect(screen.getAllByText(/John Reed/).length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText(/Dr\. Sarah Wells/)).toBeInTheDocument();
    expect(screen.getByText('Cardiology')).toBeInTheDocument();
    expect(screen.getByText('$300.00')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /cancel as admin/i })).toBeInTheDocument();
  });

  it('renders the status history timeline with actors', async () => {
    renderWithRoutes(<AdminAppointmentDetailPage />, '/admin/appointments/101');

    expect(await screen.findByText('Status history')).toBeInTheDocument();

    expect(screen.getAllByText('Scheduled').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Confirmed').length).toBeGreaterThan(0);
    expect(screen.getByText('Appointment created')).toBeInTheDocument();
    expect(screen.getByText(/by John Reed/)).toBeInTheDocument();
    expect(screen.getByText(/by Dr Sarah Wells/)).toBeInTheDocument();
  });

  it('shows the 404 state for an unknown appointment', async () => {
    mockedApi.getAdminAppointment.mockRejectedValue({
      isAxiosError: true,
      response: { status: 404, data: { message: 'Appointment not found: 999' } },
    });
    renderWithRoutes(<AdminAppointmentDetailPage />, '/admin/appointments/999');

    expect(await screen.findByText('Appointment not found')).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: /back to appointments/i }),
    ).toBeInTheDocument();
  });

  it('renders a 403 error with the friendly message', async () => {
    mockedApi.getAdminAppointment.mockRejectedValue({
      isAxiosError: true,
      response: { status: 403, data: { message: 'Access Denied' } },
    });
    renderWithRoutes(<AdminAppointmentDetailPage />, '/admin/appointments/101');

    expect(
      await screen.findByText('You are not allowed to view this appointment.'),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /try again/i })).toBeInTheDocument();
  });

  it('cancels the appointment through the confirm modal with a required reason', async () => {
    mockedApi.cancelAppointmentAsAdmin.mockResolvedValue({
      ...APPOINTMENTS[0],
      status: 'CANCELLED',
      cancellationReason: 'Patient contacted support',
    });
    renderWithRoutes(<AdminAppointmentDetailPage />, '/admin/appointments/101');
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: /cancel as admin/i }));

    const dialog = await screen.findByRole('dialog');
    expect(dialog).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /confirm cancellation/i }));
    expect(await screen.findByText('Cancellation reason is required')).toBeInTheDocument();
    expect(mockedApi.cancelAppointmentAsAdmin).not.toHaveBeenCalled();

    fireEvent.change(screen.getByLabelText('Cancellation reason'), {
      target: { value: 'Patient contacted support' },
    });
    await user.click(screen.getByRole('button', { name: /confirm cancellation/i }));

    await waitFor(() =>
      expect(mockedApi.cancelAppointmentAsAdmin).toHaveBeenCalledWith(101, 'Patient contacted support'),
    );
    expect(await screen.findByText(/recorded in the audit trail/i)).toBeInTheDocument();
  });

  it('hides the admin cancel button for finished appointments', async () => {
    mockedApi.getAdminAppointment.mockResolvedValue({
      ...DETAIL,
      appointment: { ...DETAIL.appointment, status: 'COMPLETED' },
    });
    renderWithRoutes(<AdminAppointmentDetailPage />, '/admin/appointments/101');

    expect(await screen.findByText('Appointment #101')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /cancel as admin/i })).not.toBeInTheDocument();
  });

  it('surfaces a 409 backend error inside the cancel modal', async () => {
    mockedApi.cancelAppointmentAsAdmin.mockRejectedValue({
      isAxiosError: true,
      response: { status: 409, data: { message: 'appointment is already cancelled' } },
    });
    renderWithRoutes(<AdminAppointmentDetailPage />, '/admin/appointments/101');
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: /cancel as admin/i }));
    fireEvent.change(await screen.findByLabelText('Cancellation reason'), {
      target: { value: 'second attempt' },
    });
    await user.click(screen.getByRole('button', { name: /confirm cancellation/i }));

    expect(await screen.findByText(/already cancelled/i)).toBeInTheDocument();

    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });
});

describe('AdminDashboardPage (real metrics)', () => {
  it('shows the real clinic overview numbers', async () => {
    renderWithRoutes(<AdminDashboardPage />, '/admin');

    expect(await screen.findByText('appointments booked')).toBeInTheDocument();
    expect(screen.getByText('appointments booked')).toBeInTheDocument();
    const bigNumbers = screen
      .getAllByText(/^(2|7)$/)
      .filter((el) => el.className.includes('font-display'));
    expect(bigNumbers.map((el) => el.textContent)).toEqual(expect.arrayContaining(['2', '7']));
    expect(screen.getByText(formatDate('2026-09-25'))).toBeInTheDocument();
  });

  it('shows the per-status breakdown', async () => {
    renderWithRoutes(<AdminDashboardPage />, '/admin');

    const section = screen.getByLabelText('Clinic overview');
    await within(section).findByText('appointments booked');

    expect(within(section).getByText('Scheduled').closest('li')).toHaveTextContent('3');
    expect(within(section).getByText('Confirmed').closest('li')).toHaveTextContent('1');
    expect(within(section).getByText('No show').closest('li')).toHaveTextContent('0');
  });

  it('shows an error box when the overview cannot be loaded', async () => {
    mockedApi.getAdminOverview.mockRejectedValue({
      isAxiosError: true,
      response: { status: 500, data: { message: 'Internal error' } },
    });
    renderWithRoutes(<AdminDashboardPage />, '/admin');

    expect(await screen.findByText('Internal error')).toBeInTheDocument();
  });

  it('links to the appointments management page', async () => {
    renderWithRoutes(<AdminDashboardPage />, '/admin');
    expect(screen.getByText('Manage Appointments').closest('a')).toHaveAttribute(
      'href',
      '/admin/appointments',
    );
  });
});

describe('AdminAppointmentsPage — book for a patient (support path)', () => {
  beforeEach(() => {

    mockedApi.listAdminPatients.mockResolvedValue([
      {
        id: 11, email: 'john@medbook.test', firstName: 'John', lastName: 'Reed',
        phoneNumber: '+15550002222', dateOfBirth: '1990-01-01', active: true, userActive: true,
      },
    ]);
  });

  it('books on behalf of a patient through the modal', async () => {
    const user = userEvent.setup();
    renderWithRoutes(<AdminAppointmentsPage />, '/admin/appointments');
    await screen.findByText('John Reed');

    await user.click(screen.getByRole('button', { name: /book for a patient/i }));
    const dialog = await screen.findByRole('dialog', { name: /book for a patient/i });

    await pickFromSelect(user, within(dialog), 'Patient', /John Reed \(john@medbook\.test\)/);
    await pickFromSelect(user, within(dialog), 'Specialty', 'Cardiology');

    await pickFromSelect(user, within(dialog), 'Doctor', /Dr\. Sarah Wells/);
    fireEvent.change(within(dialog).getByLabelText('Date'), { target: { value: '2026-09-28' } });
    fireEvent.change(within(dialog).getByLabelText('Start'), { target: { value: '10:00' } });
    fireEvent.change(within(dialog).getByLabelText('End'), { target: { value: '10:30' } });

    await user.click(within(dialog).getByRole('button', { name: /book appointment/i }));

    await waitFor(() =>
      expect(mockedAppointmentApi.createAppointmentForPatient).toHaveBeenCalledWith({
        patientId: 11,
        doctorId: 21,
        appointmentDate: '2026-09-28',
        startTime: '10:00',
        endTime: '10:30',
      }),
    );
  });

  it('surfaces a backend conflict inside the modal', async () => {
    mockedAppointmentApi.createAppointmentForPatient.mockRejectedValue({
      isAxiosError: true,
      response: { status: 409, data: { message: 'Doctor already has an appointment that clashes' } },
    });
    const user = userEvent.setup();
    renderWithRoutes(<AdminAppointmentsPage />, '/admin/appointments');
    await screen.findByText('John Reed');

    await user.click(screen.getByRole('button', { name: /book for a patient/i }));
    const dialog = await screen.findByRole('dialog', { name: /book for a patient/i });

    await pickFromSelect(user, within(dialog), 'Patient', /John Reed \(john@medbook\.test\)/);
    await pickFromSelect(user, within(dialog), 'Specialty', 'Cardiology');
    await pickFromSelect(user, within(dialog), 'Doctor', /Dr\. Sarah Wells/);
    fireEvent.change(within(dialog).getByLabelText('Date'), { target: { value: '2026-09-28' } });
    fireEvent.change(within(dialog).getByLabelText('Start'), { target: { value: '10:00' } });
    fireEvent.change(within(dialog).getByLabelText('End'), { target: { value: '10:30' } });
    await user.click(within(dialog).getByRole('button', { name: /book appointment/i }));

    expect(await screen.findByText(/clashes/i)).toBeInTheDocument();

    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });
});
