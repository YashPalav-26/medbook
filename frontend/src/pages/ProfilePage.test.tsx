import { render, screen, within, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Toaster } from 'sonner';
import { pickFromSelect } from '../test/mui';
import { ProfilePage } from './ProfilePage';
import * as profileApi from '../api/profileApi';
import type { CurrentUserResponse } from '../types/auth';
import { formatDate } from '../lib/dates';

vi.mock('../api/profileApi', () => ({
  getMyProfile: vi.fn(),
  updateMyAccount: vi.fn(),
  updateMyPatientProfile: vi.fn(),
  updateMyDoctorProfile: vi.fn(),
  changeMyPassword: vi.fn(),
}));

vi.mock('../hooks/useAuth', () => ({
  useAuth: () => ({
    isAuthenticated: true,
    user: { id: 1, email: 'me@medbook.test', firstName: 'Ada', lastName: 'Admin', roles: ['ROLE_ADMIN'] },
    login: vi.fn(), register: vi.fn(), logout: vi.fn(), updateUser: vi.fn(),
  }),
}));

const mockedApi = vi.mocked(profileApi);

const PATIENT_PROFILE: CurrentUserResponse = {
  id: 1,
  email: 'john@medbook.test',
  firstName: 'John',
  lastName: 'Carter',
  phoneNumber: '+91 90000 00001',
  roles: ['ROLE_PATIENT'],
  patient: {
    patientId: 11,
    dateOfBirth: '1990-05-15',
    addressLine1: '12 Beach Road',
    addressLine2: null,
    city: 'Mumbai',
    stateProvince: 'MH',
    postalCode: '400001',
    country: 'India',
    emergencyContactName: 'Rita Reed',
    emergencyContactPhone: '+91 98200 11222',
    hasInsurance: true,
    insuranceProvider: 'Blue Cross',
    insurancePolicyNumber: 'POL-7788',
    allergies: 'Peanuts',
  },
  doctor: null,
};

const DOCTOR_PROFILE: CurrentUserResponse = {
  id: 2,
  email: 'wells@medbook.test',
  firstName: 'Sarah',
  lastName: 'Wells',
  phoneNumber: '+91 90000 00002',
  roles: ['ROLE_DOCTOR'],
  patient: null,
  doctor: {
    doctorId: 21,
    bio: 'Cardiologist with 15 years of practice',
    profilePictureUrl: 'https://example.com/wells.png',
    specialtyName: 'Cardiology',
    medicalLicenseNumber: 'LIC-DOC-21',
    consultationFee: 300,
    active: true,
  },
};

const ADMIN_PROFILE: CurrentUserResponse = {
  id: 3,
  email: 'admin@medbook.test',
  firstName: 'Ada',
  lastName: 'Admin',
  phoneNumber: '+91 90000 00003',
  roles: ['ROLE_ADMIN'],
  patient: null,
  doctor: null,
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
        <ProfilePage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  mockedApi.getMyProfile.mockResolvedValue(ADMIN_PROFILE);
  mockedApi.updateMyAccount.mockResolvedValue(ADMIN_PROFILE);
  mockedApi.updateMyPatientProfile.mockResolvedValue(PATIENT_PROFILE);
  mockedApi.updateMyDoctorProfile.mockResolvedValue(DOCTOR_PROFILE);
});

describe('ProfilePage — rendering', () => {
  it('renders the account section for every role', async () => {
    renderPage();

    expect(await screen.findByText('Account')).toBeInTheDocument();
    expect(screen.getByText('Ada Admin')).toBeInTheDocument();
    expect(screen.getByText('admin@medbook.test')).toBeInTheDocument();
    expect(screen.getByText('ADMIN')).toBeInTheDocument();

    expect(screen.getAllByText('Change Password').length).toBeGreaterThanOrEqual(2);
  });

  it('shows patient profile fields with date of birth', async () => {
    mockedApi.getMyProfile.mockResolvedValue(PATIENT_PROFILE);
    renderPage();

    expect(await screen.findByText('Patient Profile')).toBeInTheDocument();
    expect(screen.getByText(formatDate('1990-05-15'))).toBeInTheDocument();
    expect(screen.getByText(/12 Beach Road/)).toBeInTheDocument();
    expect(screen.getByText(/Rita Reed/)).toBeInTheDocument();
    expect(screen.getByText('Peanuts')).toBeInTheDocument();
    expect(screen.queryByText('Professional Profile')).not.toBeInTheDocument();
  });

  it('shows doctor profile with the admin-controlled fields marked', async () => {
    mockedApi.getMyProfile.mockResolvedValue(DOCTOR_PROFILE);
    renderPage();

    expect(await screen.findByText('Professional Profile')).toBeInTheDocument();
    expect(screen.getByText(/Cardiologist with 15 years/)).toBeInTheDocument();
    expect(screen.getByText('Cardiology')).toBeInTheDocument();
    expect(screen.getByText('LIC-DOC-21')).toBeInTheDocument();
    expect(screen.getByText('$300.00')).toBeInTheDocument();
    expect(screen.queryByText('Patient Profile')).not.toBeInTheDocument();
  });

  it('shows the administrator note for admin-only accounts', async () => {
    renderPage();
    expect(await screen.findByText(/no separate admin profile/)).toBeInTheDocument();
  });

  it('renders an error box when the profile cannot be loaded', async () => {
    mockedApi.getMyProfile.mockRejectedValue({
      isAxiosError: true,
      response: { status: 500, data: { message: 'Internal error' } },
    });
    renderPage();

    expect(await screen.findByText('Internal error')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /try again/i })).toBeInTheDocument();
  });
});

describe('ProfilePage — account editing', () => {
  it('enters edit mode, validates, and saves with a toast', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole('button', { name: /edit/i }));
    expect(screen.getByLabelText('First name')).toHaveValue('Ada');

    const phoneInput = screen.getByLabelText('Phone number');
    await user.clear(phoneInput);
    await user.type(phoneInput, 'not a phone');
    await user.click(screen.getByRole('button', { name: /save changes/i }));
    expect(await screen.findByText(/digits, spaces, dashes/i)).toBeInTheDocument();
    expect(mockedApi.updateMyAccount).not.toHaveBeenCalled();

    await user.clear(phoneInput);
    await user.type(phoneInput, '+91 90000 12345');
    await user.click(screen.getByRole('button', { name: /save changes/i }));

    await waitFor(() =>
      expect(mockedApi.updateMyAccount).toHaveBeenCalledWith({
        firstName: 'Ada',
        lastName: 'Admin',
        phoneNumber: '+91 90000 12345',
      }),
    );
    expect(await screen.findByText('Account details updated.')).toBeInTheDocument();
  });

  it('cancels editing without saving', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole('button', { name: /edit/i }));
    await user.clear(screen.getByLabelText('First name'));
    await user.type(screen.getByLabelText('First name'), 'Changed');
    await user.click(screen.getByRole('button', { name: /cancel edit/i }));

    expect(await screen.findByText('Ada Admin')).toBeInTheDocument();
    expect(mockedApi.updateMyAccount).not.toHaveBeenCalled();
  });

  it('surfaces a backend error inside the account form', async () => {
    mockedApi.updateMyAccount.mockRejectedValue({
      isAxiosError: true,
      response: { status: 400, data: { message: 'Request validation failed' } },
    });
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole('button', { name: /edit/i }));
    await user.click(screen.getByRole('button', { name: /save changes/i }));

    expect(await screen.findByText('Request validation failed')).toBeInTheDocument();

    expect(screen.getByLabelText('First name')).toBeInTheDocument();
  });
});

describe('ProfilePage — patient profile editing', () => {
  it('edits patient fields and submits the full form', async () => {
    mockedApi.getMyProfile.mockResolvedValue(PATIENT_PROFILE);
    const user = userEvent.setup();
    renderPage();

    const patientCard = (await screen.findByText('Patient Profile')).closest('div[class*="rounded"]') as HTMLElement;
    await user.click(within(patientCard).getByRole('button', { name: /edit/i }));
    await user.clear(screen.getByLabelText('City'));
    await user.type(screen.getByLabelText('City'), 'Pune');

    await pickFromSelect(user, screen, 'Insurance status', 'No insurance');
    await user.click(screen.getByRole('button', { name: /save changes/i }));

    await waitFor(() =>
      expect(mockedApi.updateMyPatientProfile).toHaveBeenCalledWith(
        expect.objectContaining({ city: 'Pune', hasInsurance: false }),
      ),
    );
    expect(await screen.findByText('Patient profile updated.')).toBeInTheDocument();
  });
});

describe('ProfilePage — doctor profile editing', () => {
  it('edits bio and picture only', async () => {
    mockedApi.getMyProfile.mockResolvedValue(DOCTOR_PROFILE);
    const user = userEvent.setup();
    renderPage();

    const doctorCard = (await screen.findByText('Professional Profile')).closest('div[class*="rounded"]') as HTMLElement;
    await user.click(within(doctorCard).getByRole('button', { name: /edit/i }));
    expect(screen.getByLabelText('Bio')).toBeInTheDocument();
    expect(screen.queryByLabelText('Specialty')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Consultation fee')).not.toBeInTheDocument();

    await user.clear(screen.getByLabelText('Bio'));
    await user.type(screen.getByLabelText('Bio'), 'Updated bio');
    await user.click(screen.getByRole('button', { name: /save changes/i }));

    await waitFor(() =>
      expect(mockedApi.updateMyDoctorProfile).toHaveBeenCalledWith({
        bio: 'Updated bio',
        profilePictureUrl: 'https://example.com/wells.png',
      }),
    );
    expect(await screen.findByText('Professional profile updated.')).toBeInTheDocument();
  });
});describe('ProfilePage — change password', () => {

  function fillPasswords(current: string, next: string, confirm: string) {
    fireEvent.change(screen.getByLabelText('Current password'), { target: { value: current } });
    fireEvent.change(screen.getByLabelText('New password'), { target: { value: next } });
    fireEvent.change(screen.getByLabelText('Confirm new password'), { target: { value: confirm } });
  }

  it('validates, blocks mismatched confirmation, and succeeds', async () => {
    mockedApi.changeMyPassword.mockResolvedValue(undefined);
    const user = userEvent.setup();
    renderPage();

    await screen.findAllByText('Change Password');
    fillPasswords('Current#Pass1', 'New#Secret99', 'Different#1');
    await user.click(screen.getByRole('button', { name: /change password/i }));

    expect(await screen.findByText('Passwords do not match')).toBeInTheDocument();
    expect(mockedApi.changeMyPassword).not.toHaveBeenCalled();

    fireEvent.change(screen.getByLabelText('Confirm new password'), { target: { value: 'New#Secret99' } });
    await user.click(screen.getByRole('button', { name: /change password/i }));

    await waitFor(() =>
      expect(mockedApi.changeMyPassword).toHaveBeenCalledWith({
        currentPassword: 'Current#Pass1',
        newPassword: 'New#Secret99',
      }),
    );

    expect((await screen.findAllByText(/Password changed/)).length).toBeGreaterThanOrEqual(1);
  });

  it('rejects a short new password client-side', async () => {
    const user = userEvent.setup();
    renderPage();

    await screen.findAllByText('Change Password');
    fillPasswords('Current#Pass1', 'short', 'short');
    await user.click(screen.getByRole('button', { name: /change password/i }));

    expect(await screen.findByText('At least 8 characters')).toBeInTheDocument();
    expect(mockedApi.changeMyPassword).not.toHaveBeenCalled();
  });

  it('surfaces the 401 wrong-current-password error and keeps the form', async () => {
    mockedApi.changeMyPassword.mockRejectedValue({
      isAxiosError: true,
      response: { status: 401, data: { message: 'Current password is incorrect' } },
    });
    const user = userEvent.setup();
    renderPage();

    await screen.findAllByText('Change Password');
    fillPasswords('Wrong#Pass1', 'New#Secret99', 'New#Secret99');
    await user.click(screen.getByRole('button', { name: /change password/i }));

    expect(await screen.findByText('Current password is incorrect')).toBeInTheDocument();
    expect(screen.getByLabelText('Current password')).toBeInTheDocument();
    expect(mockedApi.updateMyAccount).not.toHaveBeenCalled();
  });
});
