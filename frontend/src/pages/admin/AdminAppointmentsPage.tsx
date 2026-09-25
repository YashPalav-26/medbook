import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAdminAppointments, useAdminDoctors } from '../../hooks/useAdmin';
import { toApiError } from '../../api/client';
import { AdminNav } from './SpecialtiesAdminPage';
import { PageHeader } from '../../components/admin/AdminUi';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { Input } from '../../components/ui/Input';
import { Select } from '../../components/ui/Select';
import { StatusBadge, EmptyState, ErrorBox, Skeleton } from '../../components/ui/Feedback';
import { CalendarSearch, IndianRupee, XCircle } from 'lucide-react';
import type { AdminAppointmentResponse, AppointmentStatus } from '../../types/appointment';

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
  const navigate = useNavigate();
  return (
    <ul className="space-y-3 md:hidden">
      {appointments.map((appointment) => (
        <li key={appointment.id}>
          <Card
            hover
            onClick={() => navigate(`/admin/appointments/${appointment.id}`)}
            className="p-4"
            aria-label={`Appointment #${appointment.id}`}
          >
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
              <p>{appointment.appointmentDate}</p>
              <p>{appointment.startTime} – {appointment.endTime}</p>
              <p>{appointment.specialty}</p>
              <p className="text-right">${appointment.consultationFeeSnapshot.toFixed(2)}</p>
            </div>
            {appointment.cancellationReason && (
              <p className="mt-2 flex items-start gap-2 rounded-field bg-danger-100/60 p-2 text-sm text-danger-700">
                <XCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
                {appointment.cancellationReason}
              </p>
            )}
          </Card>
        </li>
      ))}
    </ul>
  );
}

export function AdminAppointmentsPage() {
  const [date, setDate] = useState('');
  const [status, setStatus] = useState('');
  const [doctorId, setDoctorId] = useState('');

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
    <div className="min-h-screen bg-gray-50">
      <AdminNav />
      <main className="max-w-5xl mx-auto px-6 py-8">
        <PageHeader
          icon={<CalendarSearch className="w-8 h-8 text-blue-600" />}
          title="Appointments"
          description="Every visit booked in the clinic. Filter, open one for details and its status history."
        />

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
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="text-left text-gray-500 border-b border-gray-200">
                      <th className="py-2 pr-4 font-medium">ID</th>
                      <th className="py-2 pr-4 font-medium">Patient</th>
                      <th className="py-2 pr-4 font-medium">Doctor</th>
                      <th className="py-2 pr-4 font-medium">Specialty</th>
                      <th className="py-2 pr-4 font-medium">Date</th>
                      <th className="py-2 pr-4 font-medium">Time</th>
                      <th className="py-2 pr-4 font-medium">Status</th>
                      <th className="py-2 pr-4 font-medium">Fee</th>
                      <th className="py-2 font-medium text-right">Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {rows.map((appointment) => (
                      <tr key={appointment.id} className="border-b border-gray-100 last:border-0">
                        <td className="py-3 pr-4 text-gray-900">#{appointment.id}</td>
                        <td className="py-3 pr-4 text-gray-900">{appointment.patientName}</td>
                        <td className="py-3 pr-4 text-gray-900">Dr. {appointment.doctorName}</td>
                        <td className="py-3 pr-4 text-gray-600">{appointment.specialty}</td>
                        <td className="py-3 pr-4 text-gray-600">{appointment.appointmentDate}</td>
                        <td className="py-3 pr-4 text-gray-600">{appointment.startTime}–{appointment.endTime}</td>
                        <td className="py-3 pr-4"><StatusBadge status={appointment.status} /></td>
                        <td className="py-3 pr-4">
                          <span className="inline-flex items-center gap-1 text-gray-600">
                            <IndianRupee className="h-3.5 w-3.5" aria-hidden="true" />
                            {appointment.consultationFeeSnapshot.toFixed(2)}
                          </span>
                        </td>
                        <td className="py-3 text-right">
                          <Link
                            to={`/admin/appointments/${appointment.id}`}
                            className="font-medium text-brand-700 hover:underline"
                            aria-label={`View appointment #${appointment.id}`}
                          >
                            View
                          </Link>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </Card>

            {/* Mobile cards */}
            <AppointmentCards appointments={rows} />
          </>
        )}
      </main>
    </div>
  );
}
