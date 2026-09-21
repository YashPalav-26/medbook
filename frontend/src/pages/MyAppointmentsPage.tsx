import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useMyAppointments } from '../hooks/useMyAppointments';
import { toApiError } from '../api/client';
import { AppShell } from '../components/layout/AppShell';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { StatusBadge, EmptyState, ErrorBox, Skeleton } from '../components/ui/Feedback';
import { CalendarDays, CalendarPlus, Clock, IndianRupee, Stethoscope, XCircle } from 'lucide-react';
import type { AppointmentResponse } from '../types/appointment';

/**
 * My Appointments — appointment management view.
 * Status filter tabs (counts from real data), responsive cards (list collapses to
 * single column on mobile, no wide table), click-through to detail preserved.
 */

type StatusFilter = 'ALL' | 'UPCOMING' | 'CANCELLED';
const FILTERS: { key: StatusFilter; label: string }[] = [
  { key: 'ALL', label: 'All' },
  { key: 'UPCOMING', label: 'Upcoming' },
  { key: 'CANCELLED', label: 'Cancelled' },
];

function matchesFilter(appointment: AppointmentResponse, filter: StatusFilter): boolean {
  if (filter === 'ALL') return true;
  if (filter === 'CANCELLED') return appointment.status === 'CANCELLED';
  return appointment.status === 'SCHEDULED' || appointment.status === 'CONFIRMED';
}

export function MyAppointmentsPage() {
  const navigate = useNavigate();
  const { data: appointments, isLoading, isError, error, refetch } = useMyAppointments();
  const [filter, setFilter] = useState<StatusFilter>('ALL');

  if (isError) {
    const apiError = toApiError(error);
    return (
      <AppShell title="My Appointments">
        <ErrorBox
          message={apiError.status === 403 ? 'You are not allowed to view this list.' : apiError.message}
          onRetry={() => refetch()}
        />
      </AppShell>
    );
  }

  const all = appointments ?? [];
  const shown = all.filter((a) => matchesFilter(a, filter));
  const counts = {
    ALL: all.length,
    UPCOMING: all.filter((a) => matchesFilter(a, 'UPCOMING')).length,
    CANCELLED: all.filter((a) => matchesFilter(a, 'CANCELLED')).length,
  };

  return (
    <AppShell title="My Appointments">
      {/* Header row with primary action */}
      <div className="mb-6 flex flex-wrap items-center justify-between gap-4">
        <p className="text-ink-500">
          Every visit you have booked, newest first.
        </p>
        <Link to="/appointments/new">
          <Button>
            <CalendarPlus className="h-4 w-4" aria-hidden="true" />
            Book Appointment
          </Button>
        </Link>
      </div>

      {/* Status filter tabs — real counts, keyboard accessible radios */}
      <div role="tablist" aria-label="Filter appointments by status" className="mb-6 flex flex-wrap gap-2">
        {FILTERS.map((f) => (
          <button
            key={f.key}
            role="tab"
            aria-selected={filter === f.key}
            onClick={() => setFilter(f.key)}
            className={`rounded-full px-4 py-1.5 text-sm font-medium transition-colors ${
              filter === f.key
                ? 'bg-brand-600 text-white shadow-card'
                : 'bg-white text-ink-600 shadow-card hover:text-ink-900'
            }`}
          >
            {f.label} <span className="opacity-70">({counts[f.key]})</span>
          </button>
        ))}
      </div>

      {/* List */}
      {isLoading ? (
        <ul className="space-y-4" aria-label="Loading appointments">
          {[0, 1, 2].map((i) => (
            <li key={i}>
              <Card className="space-y-3 p-6">
                <div className="flex justify-between">
                  <Skeleton className="h-5 w-36" />
                  <Skeleton className="h-5 w-20 rounded-full" />
                </div>
                <Skeleton className="h-4 w-52" />
                <Skeleton className="h-4 w-40" />
              </Card>
            </li>
          ))}
        </ul>
      ) : shown.length === 0 ? (
        <Card className="p-6">
          <EmptyState
            icon={<CalendarDays className="h-8 w-8" />}
            title={all.length === 0 ? 'No appointments yet' : 'Nothing under this filter'}
            message={
              all.length === 0
                ? 'When you book a visit it will appear here with its time, doctor and status.'
                : 'Try a different filter to see your other appointments.'
            }
            action={
              all.length === 0 ? (
                <Link to="/appointments/new"><Button size="sm">Book Appointment</Button></Link>
              ) : (
                <Button size="sm" variant="outline" onClick={() => setFilter('ALL')}>Show all</Button>
              )
            }
          />
        </Card>
      ) : (
        <ul className="space-y-4">
          {shown.map((appointment) => (
            <li key={appointment.id}>
              <Card
                hover
                onClick={() => navigate(`/appointments/${appointment.id}`)}
                className="p-5"
                aria-label={`Appointment #${appointment.id}`}
              >
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="flex items-center gap-4">
                    <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-brand-50 text-brand-600">
                      <Stethoscope className="h-5 w-5" aria-hidden="true" />
                    </div>
                    <div>
                      <p className="font-medium text-ink-900">Appointment #{appointment.id}</p>
                      <p className="text-sm text-ink-500">
                        Doctor #{appointment.doctorId} · Patient #{appointment.patientId}
                      </p>
                    </div>
                  </div>
                  <StatusBadge status={appointment.status} />
                </div>

                <div className="mt-4 grid gap-2 text-sm text-ink-700 sm:grid-cols-3">
                  <p className="flex items-center gap-2">
                    <CalendarDays className="h-4 w-4 text-ink-400" aria-hidden="true" />
                    {appointment.appointmentDate}
                  </p>
                  <p className="flex items-center gap-2">
                    <Clock className="h-4 w-4 text-ink-400" aria-hidden="true" />
                    {appointment.startTime} – {appointment.endTime}
                  </p>
                  <p className="flex items-center gap-2">
                    <IndianRupee className="h-4 w-4 text-ink-400" aria-hidden="true" />
                    ${appointment.consultationFeeSnapshot.toFixed(2)}
                  </p>
                </div>

                {appointment.status === 'CANCELLED' && appointment.cancellationReason && (
                  <p className="mt-3 flex items-start gap-2 rounded-field bg-danger-100/60 p-2.5 text-sm text-danger-700">
                    <XCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
                    {appointment.cancellationReason}
                  </p>
                )}
              </Card>
            </li>
          ))}
        </ul>
      )}
    </AppShell>
  );
}
