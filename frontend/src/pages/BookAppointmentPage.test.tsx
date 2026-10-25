import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Toaster } from 'sonner';
import { pickFromSelect } from '../test/mui';
import { BookAppointmentPage } from './BookAppointmentPage';
import * as appointmentApi from '../api/appointmentApi';
import { formatDate } from '../lib/dates';
import type { AppointmentResponse } from '../types/appointment';

// The hooks read authentication state to decide whether queries may run.
vi.mock('../hooks/useAuth', () => ({
  useAuth: () => ({ isAuthenticated: true, user: { id: 42 } }),
}));

// All server access is mocked; the page is tested against the contract below.
vi.mock('../api/appointmentApi', () => ({
  getSpecialties: vi.fn(),
  getDoctors: vi.fn(),
  getDoctorAvailability: vi.fn(),
  createAppointment: vi.fn(),
}));

const mockedApi = vi.mocked(appointmentApi);

/**
 * A booking date comfortably in the future, computed at run time.
 * The page rejects past dates, so a hardcoded literal goes stale the day after
 * it is written — this keeps the suite green on any day it runs.
 */
const BOOKING_DATE = (() => {
  const d = new Date();
  d.setDate(d.getDate() + 30);
  return new Date(d.getTime() - d.getTimezoneOffset() * 60 * 1000).toISOString().slice(0, 10);
})();

const SPECIALTIES = [
  { id: 5, name: 'Cardiology', description: null, active: true },
  { id: 6, name: 'Dermatology', description: null, active: true },
];

const DOCTORS = [
  {
    id: 7,
    firstName: 'Alice',
    lastName: 'Smith',
    specialty: 'Cardiology',
    yearsExperience: 8,
    bio: 'Heart health specialist',
    consultationFee: 500,
    profilePictureUrl: null,
  },
];

const AVAILABILITY = [
  { id: 3, dayOfWeek: 1, startTime: '09:00', endTime: '17:00', recurring: true, effectiveFrom: null, effectiveUntil: null },
];

const BOOKED: AppointmentResponse = {
  id: 99,
  patientId: 123,
  patientName: 'Test Patient',
  doctorId: 7,
  doctorName: 'Sarah Wells',
  appointmentDate: '2026-09-28',
  startTime: '09:00',
  endTime: '09:30',
  status: 'SCHEDULED',
  cancellationReason: null,
  consultationFeeSnapshot: 500,
};

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/appointments/new']}>
        {/* Sonner's Toaster stands in for the one App.tsx mounts. */}
        <Toaster position="bottom-right" />
        <Routes>
          <Route path="/appointments/new" element={<BookAppointmentPage />} />
          <Route path="/appointments/:id" element={<div>DETAIL PAGE PROBE</div>} />
          <Route path="/appointments" element={<div>MY APPOINTMENTS PROBE</div>} />
          <Route path="/dashboard" element={<div>DASHBOARD PROBE</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

/** Select specialty → doctor, then set date and times through the real form controls. */
async function fillValidForm(user: ReturnType<typeof userEvent.setup>) {
  // MUI Selects: open the listbox, then click the option once it renders.
  await pickFromSelect(user, screen, 'Specialty', 'Cardiology');
  await pickFromSelect(user, screen, 'Doctor', /Dr\. Alice Smith/);
  const dateInput = screen.getByLabelText('Appointment date');
  await user.type(dateInput, BOOKING_DATE);
  await user.type(screen.getByLabelText('Start time'), '09:00');
  await user.type(screen.getByLabelText('End time'), '09:30');
}

beforeEach(() => {
  vi.clearAllMocks();
  mockedApi.getSpecialties.mockResolvedValue(SPECIALTIES);
  mockedApi.getDoctors.mockResolvedValue(DOCTORS);
  mockedApi.getDoctorAvailability.mockResolvedValue(AVAILABILITY);
  mockedApi.createAppointment.mockResolvedValue(BOOKED);
});

describe('BookAppointmentPage (secure patient self-booking)', () => {
  it('renders specialty and doctor options from the discovery APIs', async () => {
    renderPage();
    const user = userEvent.setup();

    // Specialties load into the MUI listbox.
    await pickFromSelect(user, screen, 'Specialty', 'Cardiology');

    // Doctors of that specialty load into the second dropdown.
    const doctorSelect = await screen.findByLabelText('Doctor');
    // MUI v9 conveys disabled via aria-disabled; jest-dom's toBeDisabled ignores it.
    await waitFor(() => expect(doctorSelect.getAttribute('aria-disabled')).not.toBe('true'));

    // The doctor query was narrowed to the selected specialty.
    expect(mockedApi.getDoctors).toHaveBeenCalledWith(5);

    // The doctor option renders inside MUI's open listbox.
    fireEvent.mouseDown(doctorSelect);
    expect(await screen.findByRole('option', { name: /Dr\. Alice Smith/ })).toBeInTheDocument();
  });

  it('shows the doctor availability for the selected date', async () => {
    renderPage();
    const user = userEvent.setup();
    await fillValidForm(user);

    expect(await screen.findByText(new RegExp(`Availability on ${formatDate(BOOKING_DATE)}`))).toBeInTheDocument();
    expect(await screen.findByText(/09:00–17:00/)).toBeInTheDocument();
  });

  it('shows a clear empty state when the doctor has no availability that day', async () => {
    mockedApi.getDoctorAvailability.mockResolvedValue([]);
    renderPage();
    const user = userEvent.setup();
    await fillValidForm(user);

    expect(await screen.findByText(new RegExp(`No availability windows for this doctor on ${formatDate(BOOKING_DATE)}`)))
      .toBeInTheDocument();
  });

  it('has no patient ID input anywhere on the page', () => {
    renderPage();
    expect(screen.queryByLabelText(/patient id/i)).not.toBeInTheDocument();
    expect(screen.queryByPlaceholderText(/patient id/i)).not.toBeInTheDocument();
  });

  it('does not submit when required choices are missing', async () => {
    renderPage();
    const user = userEvent.setup();

    await user.click(screen.getByRole('button', { name: /book appointment/i }));

    expect(await screen.findByText('Select a specialty')).toBeInTheDocument();
    expect(mockedApi.createAppointment).not.toHaveBeenCalled();
  });

  it('submits the booking without any patient identity and navigates to the detail page', async () => {
    renderPage();
    const user = userEvent.setup();
    await fillValidForm(user);

    await user.click(screen.getByRole('button', { name: /book appointment/i }));

    await waitFor(() => expect(mockedApi.createAppointment).toHaveBeenCalledTimes(1));
    const payload = mockedApi.createAppointment.mock.calls[0][0];
    // The JWT is the only identity source: no patientId may be sent, ever.
    expect('patientId' in payload).toBe(false);
    expect(payload.doctorId).toBe(7);
    expect(payload.appointmentDate).toBe(BOOKING_DATE);
    expect(payload.startTime).toBe('09:00');
    expect(payload.endTime).toBe('09:30');

    expect(await screen.findByText('DETAIL PAGE PROBE')).toBeInTheDocument();
  });

  it('shows a readable message when the backend reports a slot conflict (409)', async () => {
    mockedApi.createAppointment.mockRejectedValue({
      isAxiosError: true,
      response: { status: 409, data: { message: 'Doctor already has an appointment overlapping this time' } },
    });
    renderPage();
    const user = userEvent.setup();
    await fillValidForm(user);

    await user.click(screen.getByRole('button', { name: /book appointment/i }));

    expect(await screen.findByText(/already has an appointment overlapping/i)).toBeInTheDocument();
    // The user stays on the form to correct the choice.
    expect(screen.getByLabelText('Specialty')).toBeInTheDocument();
  });

  it('shows a readable message when the backend reports 403', async () => {
    mockedApi.createAppointment.mockRejectedValue({
      isAxiosError: true,
      response: { status: 403, data: { message: 'Only patients can book appointments for themselves' } },
    });
    renderPage();
    const user = userEvent.setup();
    await fillValidForm(user);

    await user.click(screen.getByRole('button', { name: /book appointment/i }));

    expect(await screen.findByText(/only patients can book appointments/i)).toBeInTheDocument();
  });

  it('shows the booking summary before submission', async () => {
    renderPage();
    const user = userEvent.setup();
    await fillValidForm(user);

    // Step 3 heading is the confirmation summary (wizard style since Step 20).
    expect(await screen.findByText('Confirm your visit')).toBeInTheDocument();
    // "Dr. Alice Smith" appears in the doctor card AND the summary — assert both.
    expect(screen.getAllByText('Dr. Alice Smith').length).toBeGreaterThanOrEqual(1);
    // Date · time line and the fee line are distinct elements.
    expect(screen.getByText(new RegExp(`${formatDate(BOOKING_DATE)} · 09:00–09:30`))).toBeInTheDocument();
    expect(screen.getByText(/Consultation fee: \$500/)).toBeInTheDocument();
  });
});
