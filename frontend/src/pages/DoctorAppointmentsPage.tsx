import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMyAppointments } from '../hooks/useMyAppointments';
import { toApiError } from '../api/client';
import { AppShell } from '../components/layout/AppShell';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { StatusBadge, EmptyState, ErrorBox, Skeleton } from '../components/ui/Feedback';
import { CalendarDays, Clock, IndianRupee, User, XCircle } from 'lucide-react';
import type { AppointmentResponse } from '../types/appointment';

/**
 * Doctor appointment list (GET /appointments/me — the backend returns only
 * appointments assigned to the authenticated doctor). Same design language as
 * My Appointments: status filter tabs with real counts, responsive cards,
 * click-through to the shared detail page.
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

export function DoctorAppointmentsPage() {
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
      <div className="mb-6">
        <p className="text-ink-500">
          Appointments patients have booked with you, with their current status.
        </p>
      </div>

      {/* Status filter tabs */}
      <div className="mb-6 flex flex-wrap gap-2" role="tablist" aria-label="Filter appointments by status">
        {FILTERS.map(({ key, label }) => (
          <button
            key={key}
            type="button"
            role="tab"
            aria-selected={filter === key}
            onClick={() => setFilter(key)}
            className={`rounded-full px-4 py-1.5 text-sm font-medium transition-colors ${
              filter === key
                ? 'bg-brand-600 text-white shadow-card'
                : 'bg-ink-100 text-ink-600 hover:bg-ink-200'
            }`}
          >
            {label} ({counts[key]})
          </button>
        ))}
      </div>

      {isLoading ? (
        <ul className="space-y-4">
          {[0, 1, 2].map((i) => (
            <li key={i}>
              <Card className="space-y-3 p-5">
                <Skeleton className="h-5 w-48" />
                <Skeleton className="h-4 w-64" />
                <Skeleton className="h-4 w-40" />
              </Card>
            </li>
          ))}
        </ul>
      ) : shown.length === 0 ? (
        <Card className="p-6">
          <EmptyState
            icon={<CalendarDays className="h-8 w-8" />}
            title={filter === 'ALL' ? 'No appointments yet' : `No ${labelFor(filter).toLowerCase()} appointments`}
            message={
              filter === 'ALL'
                ? 'When patients book time with you, the visits will appear here.'
                : 'Try a different filter to see your other appointments.'
            }
            action={
              filter !== 'ALL' && (
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
                      <User className="h-5 w-5" aria-hidden="true" />
                    </div>
                    <div>
                      <p className="font-medium text-ink-900">Appointment #{appointment.id}</p>
                      <p className="text-sm text-ink-500">Patient #{appointment.patientId}</p>
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

function labelFor(filter: StatusFilter): string {
  return FILTERS.find((f) => f.key === filter)?.label ?? 'matching';
}
