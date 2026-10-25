import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { z } from 'zod';
import { useAdminAppointments, useAdminDoctors, useAdminPatients } from '../../hooks/useAdmin';
import { useCreateAppointmentForPatient } from '../../hooks/useAppointment';
import { useSpecialties, useDoctorAvailability, useDoctors } from '../../hooks/useAppointment';
import { toApiError } from '../../api/client';
import { AppShell } from '../../components/layout/AppShell';
import { PageHeader } from '../../components/admin/AdminUi';
import { Button } from '../../components/ui/Button';
import { Card, LinkCard } from '../../components/ui/Card';
import { Input } from '../../components/ui/Input';
import { Select } from '../../components/ui/Select';
import { Modal } from '../../components/ui/Modal';
import { toast } from '../../lib/toast';
import { StatusBadge, EmptyState, ErrorBox, Skeleton, Spinner } from '../../components/ui/Feedback';
import { formatDate, todayIso } from '../../lib/dates';
import { formatCurrency } from '../../lib/format';
import { CalendarSearch, CalendarPlus, DollarSign, XCircle } from 'lucide-react';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Paper from '@mui/material/Paper';
import type { AdminAppointmentResponse, AppointmentStatus } from '../../types/appointment';

const bookingSchema = z.object({
  patientId: z.number({ error: 'Pick a patient' }).positive(),
  specialtyId: z.number({ error: 'Pick a specialty' }).positive(),
  doctorId: z.number({ error: 'Pick a doctor' }).positive(),
  appointmentDate: z.string().min(1, 'Date is required'),
  startTime: z.string().min(1, 'Start time is required'),
  endTime: z.string().min(1, 'End time is required'),
}).refine((data) => data.startTime < data.endTime, {
  message: 'Start time must be before end time',
  path: ['endTime'],
});
type BookingFormData = z.input<typeof bookingSchema>;
type BookingOutputData = z.output<typeof bookingSchema>;

/**
 * ADMIN appointment management — clinic-wide list with server-side filters
 * (GET /admin/appointments?date=&status=&doctorId=).
 *
 * Every filter change refetches from the backend (the filters object is part of the
 * React Query key); nothing is filtered in JavaScript. Desktop shows a table, small
 * screens the same rows as cards. Rows link to the admin detail view with the
 * status-history timeline.
 */

const STATUS_OPTIONS: AppointmentStatus[] = [
  'SCHEDULED',
  'CONFIRMED',
  'COMPLETED',
  'CANCELLED',
  'NO_SHOW',
];

function humanizeStatus(status: string): string {
  return status.replace('_', ' ');
}

function AppointmentCards({ appointments }: { appointments: AdminAppointmentResponse[] }) {
  return (
    <ul className="space-y-3 md:hidden">
      {appointments.map((appointment) => (
        <li key={appointment.id}>
          <LinkCard to={`/admin/appointments/${appointment.id}`} className="p-4">
            <div className="flex flex-wrap items-start justify-between gap-2">
              <div>
                <p className="font-medium text-ink-900">Appointment #{appointment.id}</p>
                <p className="text-sm text-ink-500">
                  {appointment.patientName} → Dr. {appointment.doctorName}
                </p>
              </div>
              <StatusBadge status={appointment.status} />
            </div>
            <div className="mt-3 grid grid-cols-2 gap-2 text-sm text-ink-700">
              <p>{formatDate(appointment.appointmentDate)}</p>
              <p>{appointment.startTime} – {appointment.endTime}</p>
              <p>{appointment.specialty}</p>
              <p className="text-right">{formatCurrency(appointment.consultationFeeSnapshot)}</p>
            </div>
            {appointment.cancellationReason && (
              <p className="mt-2 flex items-start gap-2 rounded-field bg-danger-100/60 p-2 text-sm text-danger-700">
                <XCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
                {appointment.cancellationReason}
              </p>
            )}
          </LinkCard>
        </li>
      ))}
    </ul>
  );
}

/**
 * "Book for a patient" modal — the frontend face of POST /api/v1/appointments/admin
 * (the ADMIN support path for patients who cannot use the app). Doctor discovery and
 * availability guidance reuse the same public APIs the booking page uses; the
 * backend re-validates everything (role, patient, doctor, availability, conflicts).
 */
function BookForPatientModal({
  onClose,
  onBooked,
}: {
  onClose: () => void;
  onBooked: (appointmentId: number) => void;
}) {
  const patientsQuery = useAdminPatients();
  const specialtiesQuery = useSpecialties();
  const [form, setForm] = useState<Partial<BookingFormData>>({});
  const [errors, setErrors] = useState<Record<string, string>>({});

  const specialtyId = form.specialtyId ? Number(form.specialtyId) : null;
  const doctorId = form.doctorId ? Number(form.doctorId) : null;
  const doctorsQuery = useDoctors(specialtyId);
  const availabilityQuery = useDoctorAvailability(doctorId, form.appointmentDate ?? null);
  const bookingMutation = useCreateAppointmentForPatient();
  // Toasts come from the Sonner-based facade (lib/toast) — no provider needed.

  const setField = (field: keyof BookingFormData, value: string) => {
    setForm((current) => ({ ...current, [field]: value }));
    setErrors((current) => {
      const next = { ...current };
      delete next[field];
      return next;
    });
  };

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const parsed = bookingSchema.safeParse({
      patientId: form.patientId === undefined ? '' : Number(form.patientId),
      specialtyId: form.specialtyId === undefined ? '' : Number(form.specialtyId),
      doctorId: form.doctorId === undefined ? '' : Number(form.doctorId),
      appointmentDate: form.appointmentDate ?? '',
      startTime: form.startTime ?? '',
      endTime: form.endTime ?? '',
    });
    if (!parsed.success) {
      const fieldErrors: Record<string, string> = {};
      parsed.error.issues.forEach((issue) => {
        const key = issue.path[0];
        if (typeof key === 'string' && !fieldErrors[key]) fieldErrors[key] = issue.message;
      });
      setErrors(fieldErrors);
      return;
    }
    try {
      const payload = parsed.data as BookingOutputData;
      const created = await bookingMutation.mutateAsync({
        patientId: payload.patientId,
        doctorId: payload.doctorId,
        appointmentDate: payload.appointmentDate,
        startTime: payload.startTime,
        endTime: payload.endTime,
      });
      toast.success(`Appointment #${created.id} booked for the patient.`);
      onBooked(created.id);
    } catch {
      // Backend error (conflict/availability/404) shown inside the modal.
    }
  };

  const bookingError = bookingMutation.error ? toApiError(bookingMutation.error) : null;

  return (
    <Modal
      title="Book for a patient"
      description="Support path: book on behalf of a patient who cannot use the app. The patient is notified like any other booking."
      onClose={onClose}
    >
      <form onSubmit={submit} className="space-y-4">
        <Select
          label="Patient"
          value={form.patientId ?? ''}
          onChange={(e) => setField('patientId', e.target.value)}
          error={errors.patientId}
          disabled={patientsQuery.isLoading}
        >
          <option value="">Select a patient…</option>
          {(patientsQuery.data ?? []).map((p) => (
            <option key={p.id} value={p.id}>
              {p.firstName} {p.lastName} ({p.email})
            </option>
          ))}
        </Select>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Select
            label="Specialty"
            value={form.specialtyId ?? ''}
            onChange={(e) => {
              setField('specialtyId', e.target.value);
              setField('doctorId', '');
            }}
            error={errors.specialtyId}
            disabled={specialtiesQuery.isLoading}
          >
            <option value="">Select a specialty…</option>
            {(specialtiesQuery.data ?? []).map((s) => (
              <option key={s.id} value={s.id}>{s.name}</option>
            ))}
          </Select>
          <Select
            label="Doctor"
            value={form.doctorId ?? ''}
            onChange={(e) => setField('doctorId', e.target.value)}
            error={errors.doctorId}
            disabled={!specialtyId || doctorsQuery.isLoading}
          >
            <option value="">
              {specialtyId ? 'Select a doctor…' : 'Select a specialty first'}
            </option>
            {(doctorsQuery.data ?? []).map((d: { id: number; firstName: string; lastName: string }) => (
              <option key={d.id} value={d.id}>
                Dr. {d.firstName} {d.lastName}
              </option>
            ))}
          </Select>
        </div>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <Input
            label="Date"
            type="date"
            value={form.appointmentDate ?? ''}
            min={todayIso()}
            onChange={(e) => setField('appointmentDate', e.target.value)}
            error={errors.appointmentDate}
          />
          <Input
            label="Start"
            type="time"
            value={form.startTime ?? ''}
            onChange={(e) => setField('startTime', e.target.value)}
            error={errors.startTime}
          />
          <Input
            label="End"
            type="time"
            value={form.endTime ?? ''}
            onChange={(e) => setField('endTime', e.target.value)}
            error={errors.endTime}
          />
        </div>

        {/* Availability guidance for the chosen doctor/date — the backend re-validates. */}
        {doctorId && form.appointmentDate && (
          availabilityQuery.isLoading ? (
            <Skeleton className="h-8 w-48 rounded-full" />
          ) : availabilityQuery.data && availabilityQuery.data.length > 0 ? (
            <p className="rounded-field border border-success-200 bg-success-100/60 p-3 text-sm text-success-700">
              {availabilityQuery.data.length} window{availabilityQuery.data.length > 1 ? 's' : ''} open
              for this doctor on {formatDate(form.appointmentDate ?? '')}.
            </p>
          ) : (
            <p className="rounded-field border border-warning-100 bg-warning-100/60 p-3 text-sm text-warning-700">
              No availability windows for this doctor on {formatDate(form.appointmentDate ?? '')}. The booking
              will be rejected unless the window exists.
            </p>
          )
        )}

        {bookingError && <ErrorBox message={bookingError.message} />}

        <div className="flex justify-end gap-3 pt-2">
          <Button type="button" variant="outline" onClick={onClose}>Cancel</Button>
          <Button type="submit" disabled={bookingMutation.isPending}>
            {bookingMutation.isPending ? (
              <span className="flex items-center gap-2"><Spinner className="text-white" /> Booking…</span>
            ) : (
              <span className="flex items-center gap-2"><CalendarPlus className="h-4 w-4" aria-hidden="true" /> Book Appointment</span>
            )}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

export function AdminAppointmentsPage() {
  const [date, setDate] = useState('');
  const [status, setStatus] = useState('');
  const [doctorId, setDoctorId] = useState('');
  const [bookingOpen, setBookingOpen] = useState(false);

  // "" means "no filter" — the API call simply omits that param.
  const filters = {
    date: date || undefined,
    status: (status || undefined) as '' | AppointmentStatus | undefined,
    doctorId: doctorId === '' ? undefined : Number(doctorId),
  };
  const { data: appointments, isLoading, isError, error, refetch, isFetching } =
    useAdminAppointments(filters);
  const { data: doctors } = useAdminDoctors();

  const hasActiveFilters = Boolean(date || status || doctorId);
  const clearFilters = () => {
    setDate('');
    setStatus('');
    setDoctorId('');
  };

  const apiError = isError ? toApiError(error) : null;
  const rows = appointments ?? [];

  return (
    <AppShell>
      <div className="mx-auto max-w-5xl">
        <PageHeader
          icon={<CalendarSearch className="h-6 w-6 text-brand-600" />}
          title="Appointments"
          description="Every visit booked in the clinic. Filter, open one for details and its status history."
        />

        <div className="mb-4 flex justify-end">
          <Button onClick={() => setBookingOpen(true)}>
            <CalendarPlus className="h-4 w-4" aria-hidden="true" />
            Book for a Patient
          </Button>
        </div>

        {/* Filters — each change refetches from the backend */}
        <Card className="mb-6 p-4">
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 md:grid-cols-4 md:items-end">
            <Input
              label="Date"
              type="date"
              value={date}
              onChange={(e) => setDate(e.target.value)}
              aria-label="Filter by date"
            />
            <Select
              label="Status"
              value={status}
              onChange={(e) => setStatus(e.target.value)}
              aria-label="Filter by status"
            >
              <option value="">All statuses</option>
              {STATUS_OPTIONS.map((option) => (
                <option key={option} value={option}>{humanizeStatus(option)}</option>
              ))}
            </Select>
            <Select
              label="Doctor"
              value={doctorId}
              onChange={(e) => setDoctorId(e.target.value)}
              aria-label="Filter by doctor"
            >
              <option value="">All doctors</option>
              {(doctors ?? []).map((doctor) => (
                <option key={doctor.id} value={doctor.id}>
                  Dr. {doctor.firstName} {doctor.lastName}
                </option>
              ))}
            </Select>
            <Button
              type="button"
              variant="outline"
              onClick={clearFilters}
              disabled={!hasActiveFilters}
              className="justify-center"
            >
              Clear Filters
            </Button>
          </div>
        </Card>

        {apiError ? (
          <Card className="p-6">
            <ErrorBox
              message={
                apiError.status === 403
                  ? 'You are not allowed to view clinic appointments.'
                  : apiError.message
              }
              onRetry={() => refetch()}
            />
          </Card>
        ) : isLoading ? (
          <Card className="space-y-3 p-6">
            <Skeleton className="h-5 w-48" />
            <Skeleton className="h-4 w-full" />
            <Skeleton className="h-4 w-full" />
            <Skeleton className="h-4 w-2/3" />
          </Card>
        ) : rows.length === 0 ? (
          <Card className="p-6">
            <EmptyState
              icon={<CalendarSearch className="h-8 w-8" />}
              title={hasActiveFilters ? 'No appointments match these filters' : 'No appointments yet'}
              message={
                hasActiveFilters
                  ? 'Try widening the date, or clearing the status and doctor filters.'
                  : 'When patients book visits, they will appear here.'
              }
              action={hasActiveFilters && <Button variant="outline" onClick={clearFilters}>Clear Filters</Button>}
            />
          </Card>
        ) : (
          <>
            <p className="mb-3 text-sm text-ink-500" aria-live="polite">
              {rows.length} appointment{rows.length === 1 ? '' : 's'} found
              {isFetching ? ' — refreshing…' : ''}
            </p>

            {/* Desktop table */}
            <Card className="hidden md:block p-6">
              <TableContainer component={Paper} className="!bg-transparent !shadow-none">
                <Table size="small" aria-label="Clinic appointments">
                  <TableHead>
                    <TableRow>
                      <TableCell>ID</TableCell>
                      <TableCell>Patient</TableCell>
                      <TableCell>Doctor</TableCell>
                      <TableCell>Specialty</TableCell>
                      <TableCell>Date</TableCell>
                      <TableCell>Time</TableCell>
                      <TableCell>Status</TableCell>
                      <TableCell>Fee</TableCell>
                      <TableCell align="right">Actions</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {rows.map((appointment) => (
                      <TableRow key={appointment.id} hover>
                        <TableCell className="text-ink-900">#{appointment.id}</TableCell>
                        <TableCell className="text-ink-900">{appointment.patientName}</TableCell>
                        <TableCell className="text-ink-900">Dr. {appointment.doctorName}</TableCell>
                        <TableCell className="text-ink-600">{appointment.specialty}</TableCell>
                        <TableCell className="text-ink-600">{formatDate(appointment.appointmentDate)}</TableCell>
                        <TableCell className="text-ink-600">{appointment.startTime}–{appointment.endTime}</TableCell>
                        <TableCell><StatusBadge status={appointment.status} /></TableCell>
                        <TableCell>
                          <span className="inline-flex items-center gap-1 text-ink-600">
                            <DollarSign className="h-3.5 w-3.5" aria-hidden="true" />
                            {formatCurrency(appointment.consultationFeeSnapshot)}
                          </span>
                        </TableCell>
                        <TableCell align="right">
                          <Link
                            to={`/admin/appointments/${appointment.id}`}
                            className="font-medium text-brand-700 hover:underline"
                            aria-label={`View appointment #${appointment.id}`}
                          >
                            View
                          </Link>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            </Card>

            {/* Mobile cards */}
            <AppointmentCards appointments={rows} />
          </>
        )}
      </div>

      {bookingOpen && (
        <BookForPatientModal
          onClose={() => setBookingOpen(false)}
          onBooked={() => setBookingOpen(false)}
        />
      )}
    </AppShell>
  );
}
