import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AdminDashboardPage } from './AdminDashboardPage';
import { SpecialtiesAdminPage } from './SpecialtiesAdminPage';
import { DoctorsAdminPage } from './DoctorsAdminPage';
import { AdminManagementPage } from './AdminManagementPage';
import { AvailabilityAdminPage } from './AvailabilityAdminPage';
import * as adminApi from '../../api/adminApi';
import { pickFromSelect } from '../../test/mui';
import type { SpecialtyResponse } from '../../types/appointment';

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
  listAdminAvailability: vi.fn(),
  createAvailability: vi.fn(),
  updateAvailability: vi.fn(),
  deactivateAvailability: vi.fn(),
  listAdminAppointments: vi.fn(),
  getAdminAppointment: vi.fn(),
  getAdminOverview: vi.fn(),
  cancelAppointmentAsAdmin: vi.fn(),
  listAdminAccounts: vi.fn(),
  createAdminAccount: vi.fn(),
}));

vi.mock('../../hooks/useAuth', () => ({
  useAuth: () => ({
    isAuthenticated: true,
    user: { id: 1, roles: ['ROLE_ADMIN'] },
    login: vi.fn(), register: vi.fn(), logout: vi.fn(),
  }),
}));

vi.mock('../../api/client', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/client')>()),
}));

const mockedApi = vi.mocked(adminApi);

const SPECIALTIES: SpecialtyResponse[] = [
  { id: 10, name: 'Cardiology', description: 'Hearts', active: true },
  { id: 11, name: 'Old Specialty', description: null, active: false },
];

const DOCTORS: adminApi.DoctorAdminResponse[] = [
  {
    id: 20, email: 'doc@medbook.test', firstName: 'Alice', lastName: 'Smith',
    phoneNumber: '+15550001111', specialtyId: 10, specialty: 'Cardiology',
    medicalLicenseNumber: 'LIC-20', yearsExperience: 7, bio: null,
    consultationFee: 300, profilePictureUrl: null, active: true, userActive: true,
  },
];

const WINDOWS = [
  { id: 30, dayOfWeek: 1, startTime: '09:00', endTime: '17:00', recurring: true, effectiveFrom: null, effectiveUntil: null },
];

const ADMINS: adminApi.AdminAccountResponse[] = [
  {
    id: 40, email: 'yashpalav100@gmail.com', firstName: 'Yash', lastName: 'Palav',
    phoneNumber: '+10000000000', active: true, locked: false, roles: ['ADMIN'],
  },
];

function renderWithRoutes(ui: React.ReactElement, initialPath: string) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[initialPath]}>
        <Routes>
          <Route path="/admin" element={ui} />
          <Route path="/admin/specialties" element={ui} />
          <Route path="/admin/doctors" element={ui} />
          <Route path="/admin/availability" element={ui} />
          <Route path="*" element={<div>NOT FOUND</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  mockedApi.listAdminSpecialties.mockResolvedValue(SPECIALTIES);
  mockedApi.listAdminDoctors.mockResolvedValue(DOCTORS);
  mockedApi.listAdminAvailability.mockResolvedValue(WINDOWS);
  mockedApi.listAdminAccounts.mockResolvedValue(ADMINS);

  mockedApi.getAdminOverview.mockResolvedValue({
    todayAppointments: 0,
    today: '2026-09-25',
    statusCounts: { SCHEDULED: 0, CONFIRMED: 0, COMPLETED: 0, CANCELLED: 0, NO_SHOW: 0 },
    totalAppointments: 0,
  });
});

describe('AdminDashboardPage', () => {
  it('renders the three management cards', () => {
    renderWithRoutes(<AdminDashboardPage />, '/admin');
    expect(screen.getByText('Manage Specialties')).toBeInTheDocument();
    expect(screen.getByText('Manage Doctors')).toBeInTheDocument();
    expect(screen.getByText('Manage Availability')).toBeInTheDocument();
  });

  it('links to the three management pages', () => {
    renderWithRoutes(<AdminDashboardPage />, '/admin');
    expect(screen.getByText('Manage Specialties').closest('a')).toHaveAttribute('href', '/admin/specialties');
    expect(screen.getByText('Manage Doctors').closest('a')).toHaveAttribute('href', '/admin/doctors');
    expect(screen.getByText('Manage Availability').closest('a')).toHaveAttribute('href', '/admin/availability');
  });
});

describe('SpecialtiesAdminPage', () => {
  it('lists specialties with their active/inactive state', async () => {
    renderWithRoutes(<SpecialtiesAdminPage />, '/admin/specialties');
    expect(await screen.findByText('Cardiology')).toBeInTheDocument();
    expect(screen.getByText('Old Specialty')).toBeInTheDocument();

    expect(screen.getAllByText('Active').length).toBe(1);
    expect(screen.getAllByText('Inactive').length).toBe(1);
  });

  it('shows an empty state when there are no specialties', async () => {
    mockedApi.listAdminSpecialties.mockResolvedValue([]);
    renderWithRoutes(<SpecialtiesAdminPage />, '/admin/specialties');
    expect(await screen.findByText(/No specialties yet/)).toBeInTheDocument();
  });

  it('creates a specialty through the modal', async () => {
    mockedApi.createSpecialty.mockResolvedValue({ id: 12, name: 'Neurology', description: '', active: true });
    renderWithRoutes(<SpecialtiesAdminPage />, '/admin/specialties');
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: /new specialty/i }));
    await user.type(screen.getByLabelText('Name'), 'Neurology');
    await user.click(screen.getByRole('button', { name: /create specialty/i }));

    await waitFor(() => expect(mockedApi.createSpecialty).toHaveBeenCalledWith({ name: 'Neurology', description: '' }));
  });

  it('shows the backend duplicate-name error inside the modal', async () => {
    mockedApi.createSpecialty.mockRejectedValue({
      isAxiosError: true,
      response: { status: 409, data: { message: 'Specialty name already exists: Cardiology' } },
    });
    renderWithRoutes(<SpecialtiesAdminPage />, '/admin/specialties');
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: /new specialty/i }));
    await user.type(screen.getByLabelText('Name'), 'Cardiology');
    await user.click(screen.getByRole('button', { name: /create specialty/i }));

    expect(await screen.findByText(/already exists: Cardiology/)).toBeInTheDocument();

    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it('blocks submission when the name is empty', async () => {
    renderWithRoutes(<SpecialtiesAdminPage />, '/admin/specialties');
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: /new specialty/i }));
    await user.click(screen.getByRole('button', { name: /create specialty/i }));

    expect(await screen.findByText('Name is required')).toBeInTheDocument();
    expect(mockedApi.createSpecialty).not.toHaveBeenCalled();
  });

  it('deactivates a specialty from the list', async () => {
    mockedApi.deactivateSpecialty.mockResolvedValue({ ...SPECIALTIES[0], active: false });
    renderWithRoutes(<SpecialtiesAdminPage />, '/admin/specialties');
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: /deactivate cardiology/i }));
    await waitFor(() => expect(mockedApi.deactivateSpecialty).toHaveBeenCalledWith(10));
  });
});

describe('DoctorsAdminPage', () => {
  it('lists doctors with specialty, fee and status', async () => {
    renderWithRoutes(<DoctorsAdminPage />, '/admin/doctors');
    expect(await screen.findByText('Dr. Alice Smith')).toBeInTheDocument();
    expect(screen.getByText('Cardiology')).toBeInTheDocument();
    expect(screen.getByText('$300.00')).toBeInTheDocument();
  });

  it('shows an empty state when there are no doctors', async () => {
    mockedApi.listAdminDoctors.mockResolvedValue([]);
    renderWithRoutes(<DoctorsAdminPage />, '/admin/doctors');
    expect(await screen.findByText(/No doctors yet/)).toBeInTheDocument();
  });

  it('creates a doctor with account credentials and profile fields', async () => {
    mockedApi.createDoctor.mockResolvedValue(DOCTORS[0]);
    renderWithRoutes(<DoctorsAdminPage />, '/admin/doctors');
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: /new doctor/i }));
    await user.type(screen.getByLabelText('Email (login)'), 'newdoc@medbook.test');
    await user.type(screen.getByLabelText('Password (min 8 chars)'), 'Password#1');
    await user.type(screen.getByLabelText('First name'), 'New');
    await user.type(screen.getByLabelText('Last name'), 'Doc');
    await user.type(screen.getByLabelText('Phone number'), '+15550001234');
    await pickFromSelect(user, screen, 'Specialty', 'Cardiology');
    await user.type(screen.getByLabelText('Medical license number'), 'LIC-NEW');
    await user.type(screen.getByLabelText('Years of experience'), '5');
    await user.type(screen.getByLabelText('Consultation fee'), '275');
    await user.click(screen.getByRole('button', { name: /create doctor/i }));

    await waitFor(() => expect(mockedApi.createDoctor).toHaveBeenCalledTimes(1));
    const payload = mockedApi.createDoctor.mock.calls[0][0];
    expect(payload.email).toBe('newdoc@medbook.test');
    expect(payload.password).toBe('Password#1');
    expect(payload.specialtyId).toBe(10);
    expect(payload.yearsExperience).toBe(5);
    expect(payload.consultationFee).toBe(275);
  });

  it('surfaces backend validation errors (duplicate email 409) in the modal', async () => {
    mockedApi.createDoctor.mockRejectedValue({
      isAxiosError: true,
      response: { status: 409, data: { message: 'An account with this email address already exists' } },
    });
    renderWithRoutes(<DoctorsAdminPage />, '/admin/doctors');
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: /new doctor/i }));
    await user.type(screen.getByLabelText('Email (login)'), 'taken@medbook.test');
    await user.type(screen.getByLabelText('Password (min 8 chars)'), 'Password#1');
    await user.type(screen.getByLabelText('First name'), 'X');
    await user.type(screen.getByLabelText('Last name'), 'Y');
    await user.type(screen.getByLabelText('Phone number'), '+15550001234');
    await pickFromSelect(user, screen, 'Specialty', 'Cardiology');
    await user.type(screen.getByLabelText('Medical license number'), 'LIC-X');
    await user.type(screen.getByLabelText('Years of experience'), '5');
    await user.type(screen.getByLabelText('Consultation fee'), '275');
    await user.click(screen.getByRole('button', { name: /create doctor/i }));

    expect(await screen.findByText(/already exists/)).toBeInTheDocument();
  });

  it('blocks submission with client-side validation errors', async () => {
    renderWithRoutes(<DoctorsAdminPage />, '/admin/doctors');
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: /new doctor/i }));
    await user.click(screen.getByRole('button', { name: /create doctor/i }));

    expect(await screen.findByText('Email is required')).toBeInTheDocument();
    expect(screen.getByText('At least 8 characters')).toBeInTheDocument();
    expect(mockedApi.createDoctor).not.toHaveBeenCalled();
  });
});

describe('AdminManagementPage', () => {
  it('lists administrator accounts with name, email and status — never passwords', async () => {
    renderWithRoutes(<AdminManagementPage />, '/admin/specialties');
    expect(await screen.findByText('Yash Palav')).toBeInTheDocument();
    expect(screen.getByText('yashpalav100@gmail.com')).toBeInTheDocument();
    expect(screen.getAllByText('Active').length).toBeGreaterThan(0);

    expect(screen.queryByText(/password/i)).not.toBeInTheDocument();
  });

  it('shows an empty state when there are no administrators', async () => {
    mockedApi.listAdminAccounts.mockResolvedValue([]);
    renderWithRoutes(<AdminManagementPage />, '/admin/specialties');
    expect(await screen.findByText(/No administrator accounts yet/)).toBeInTheDocument();
  });

  it('creates an admin with account credentials (no role field is sent)', async () => {
    mockedApi.createAdminAccount.mockResolvedValue({ ...ADMINS[0], id: 41, email: 'new@medbook.test' });
    renderWithRoutes(<AdminManagementPage />, '/admin/specialties');
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: /add admin/i }));
    await user.type(screen.getByLabelText('Email (login)'), 'new@medbook.test');
    await user.type(screen.getByLabelText('Password (min 8 chars)'), 'Password#1');
    await user.type(screen.getByLabelText('First name'), 'Nia');
    await user.type(screen.getByLabelText('Last name'), 'Admin');
    await user.type(screen.getByLabelText('Phone number'), '+15550009999');
    await user.click(screen.getByRole('button', { name: /create admin/i }));

    await waitFor(() => expect(mockedApi.createAdminAccount).toHaveBeenCalledTimes(1));
    const payload = mockedApi.createAdminAccount.mock.calls[0][0];
    expect(payload).toEqual({
      email: 'new@medbook.test',
      password: 'Password#1',
      firstName: 'Nia',
      lastName: 'Admin',
      phoneNumber: '+15550009999',
    });
    expect('role' in payload).toBe(false);
  });

  it('surfaces backend duplicate-email errors inside the modal', async () => {
    mockedApi.createAdminAccount.mockRejectedValue({
      isAxiosError: true,
      response: { status: 409, data: { message: 'An account with this email address already exists' } },
    });
    renderWithRoutes(<AdminManagementPage />, '/admin/specialties');
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: /add admin/i }));
    await user.type(screen.getByLabelText('Email (login)'), 'taken@medbook.test');
    await user.type(screen.getByLabelText('Password (min 8 chars)'), 'Password#1');
    await user.type(screen.getByLabelText('First name'), 'X');
    await user.type(screen.getByLabelText('Last name'), 'Y');
    await user.type(screen.getByLabelText('Phone number'), '+15550009999');
    await user.click(screen.getByRole('button', { name: /create admin/i }));

    expect(await screen.findByText(/already exists/)).toBeInTheDocument();
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it('blocks submission with client-side validation errors', async () => {
    renderWithRoutes(<AdminManagementPage />, '/admin/specialties');
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: /add admin/i }));
    await user.click(screen.getByRole('button', { name: /create admin/i }));

    expect(await screen.findByText('Email is required')).toBeInTheDocument();
    expect(screen.getByText('At least 8 characters')).toBeInTheDocument();
    expect(mockedApi.createAdminAccount).not.toHaveBeenCalled();
  });
});

describe('AvailabilityAdminPage', () => {
  async function pickDoctor(user: ReturnType<typeof userEvent.setup>) {

    fireEvent.mouseDown(await screen.findByLabelText('Doctor'));
    await user.click(await screen.findByRole('option', { name: /Dr\. Alice Smith/ }));
    await screen.findByText('Weekly schedule');
  }

  it('shows the doctor picker first, without any schedule', () => {
    renderWithRoutes(<AvailabilityAdminPage />, '/admin/availability');
    expect(screen.getByLabelText('Doctor')).toBeInTheDocument();
    expect(screen.queryByText('Weekly schedule')).not.toBeInTheDocument();
  });

  it('lists the chosen doctor’s availability windows', async () => {
    renderWithRoutes(<AvailabilityAdminPage />, '/admin/availability');
    const user = userEvent.setup();
    await pickDoctor(user);

    expect(await screen.findByText('Monday')).toBeInTheDocument();
    expect(screen.getByText('09:00–17:00')).toBeInTheDocument();
    expect(screen.getByText('Weekly')).toBeInTheDocument();
    expect(screen.getByText('Open')).toBeInTheDocument();
  });

  it('creates a window using friendly day names', async () => {
    mockedApi.createAvailability.mockResolvedValue(WINDOWS[0]);
    renderWithRoutes(<AvailabilityAdminPage />, '/admin/availability');
    const user = userEvent.setup();
    await pickDoctor(user);

    await user.click(screen.getByRole('button', { name: /new window/i }));
    await pickFromSelect(user, screen, 'Day', 'Tuesday');
    await user.type(screen.getByLabelText('Start time'), '10:00');
    await user.type(screen.getByLabelText('End time'), '12:00');
    await user.click(screen.getByRole('button', { name: /add window/i }));

    await waitFor(() => expect(mockedApi.createAvailability).toHaveBeenCalledTimes(1));
    const [doctorIdArg, payloadArg] = mockedApi.createAvailability.mock.calls[0];
    expect(doctorIdArg).toBe(20);
    expect(payloadArg.dayOfWeek).toBe(2);
    expect(payloadArg.recurring).toBe(true);
  });

  it('blocks invalid time windows client-side', async () => {
    renderWithRoutes(<AvailabilityAdminPage />, '/admin/availability');
    const user = userEvent.setup();
    await pickDoctor(user);

    await user.click(screen.getByRole('button', { name: /new window/i }));
    await pickFromSelect(user, screen, 'Day', 'Tuesday');
    await user.type(screen.getByLabelText('Start time'), '17:00');
    await user.type(screen.getByLabelText('End time'), '09:00');
    await user.click(screen.getByRole('button', { name: /add window/i }));

    expect(await screen.findByText('Start time must be before end time')).toBeInTheDocument();
    expect(mockedApi.createAvailability).not.toHaveBeenCalled();
  });

  it('closes (deactivates) a window from the list', async () => {
    mockedApi.deactivateAvailability.mockResolvedValue({ ...WINDOWS[0], effectiveUntil: '2026-09-20' });
    renderWithRoutes(<AvailabilityAdminPage />, '/admin/availability');
    const user = userEvent.setup();
    await pickDoctor(user);

    await user.click(await screen.findByRole('button', { name: /close window/i }));
    await waitFor(() => expect(mockedApi.deactivateAvailability).toHaveBeenCalledWith(30));
  });
});
