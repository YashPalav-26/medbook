import { useRef, useState } from 'react';
import { useMyAppointments } from '../hooks/useMyAppointments';
import { toApiError } from '../api/client';
import { AppShell } from '../components/layout/AppShell';
import { Button, ButtonLink } from '../components/ui/Button';
import { Card, LinkCard } from '../components/ui/Card';
import { StatusBadge, EmptyState, ErrorBox, Skeleton } from '../components/ui/Feedback';
import { CalendarDays, CalendarPlus, Clock, DollarSign, Stethoscope, XCircle } from 'lucide-react';
import { formatDate, todayIso } from '../lib/dates';
import { formatCurrency } from '../lib/format';
import type { AppointmentResponse } from '../types/appointment';

type StatusFilter = 'ALL' | 'UPCOMING' | 'CANCELLED';
const FILTERS: { key: StatusFilter; label: string }[] = [
  { key: 'ALL', label: 'All' },
  { key: 'UPCOMING', label: 'Upcoming' },
  { key: 'CANCELLED', label: 'Cancelled' },
];

function matchesFilter(appointment: AppointmentResponse, filter: StatusFilter): boolean {
  if (filter === 'ALL') return true;
  if (filter === 'CANCELLED') return appointment.status === 'CANCELLED';

  const open = appointment.status === 'SCHEDULED' || appointment.status === 'CONFIRMED';
  return open && appointment.appointmentDate >= todayIso();
}

export function MyAppointmentsPage() {
  const { data: appointments, isLoading, isError, error, refetch } = useMyAppointments();
  const [filter, setFilter] = useState<StatusFilter>('ALL');
  const tabRefs = useRef<Array<HTMLButtonElement | null>>([]);

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

  const onTabKeyDown = (event: React.KeyboardEvent, index: number) => {
    const delta = event.key === 'ArrowRight' ? 1 : event.key === 'ArrowLeft' ? -1 : 0;
    if (delta === 0) return;
    event.preventDefault();
    const nextIndex = (index + delta + FILTERS.length) % FILTERS.length;
    setFilter(FILTERS[nextIndex].key);
    tabRefs.current[nextIndex]?.focus();
  };

  return (
    <AppShell title="My Appointments">
      {/* Header row with primary action */}
      <div className="mb-6 flex flex-wrap items-center justify-between gap-4">
        <p className="text-ink-500">
          Every visit you have booked, newest first.
        </p>
        <ButtonLink to="/appointments/new">
          <CalendarPlus className="h-4 w-4" aria-hidden="true" />
          Book Appointment
        </ButtonLink>
      </div>

      {/* Status filter tabs — real counts, keyboard accessible radios */}
      <div role="tablist" aria-label="Filter appointments by status" className="mb-6 flex flex-wrap gap-2">
        {FILTERS.map((f, index) => (
          <button
            key={f.key}
            ref={(element) => { tabRefs.current[index] = element; }}
            type="button"
            role="tab"
            id={`appointments-tab-${f.key}`}
            aria-selected={filter === f.key}
            aria-controls="appointments-panel"
            tabIndex={filter === f.key ? 0 : -1}
            onClick={() => setFilter(f.key)}
            onKeyDown={(event) => onTabKeyDown(event, index)}
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
      <div id="appointments-panel" role="tabpanel" aria-labelledby={`appointments-tab-${filter}`}>
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
                <ButtonLink to="/appointments/new" size="sm">Book Appointment</ButtonLink>
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
              <LinkCard to={`/appointments/${appointment.id}`} className="p-5">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="flex items-center gap-4">
                    <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-brand-50 text-brand-600">
                      <Stethoscope className="h-5 w-5" aria-hidden="true" />
                    </div>
                    <div>
                      <p className="font-medium text-ink-900">Dr. {appointment.doctorName}</p>
                      <p className="text-sm text-ink-500">Appointment #{appointment.id}</p>
                    </div>
                  </div>
                  <StatusBadge status={appointment.status} />
                </div>

                <div className="mt-4 grid gap-2 text-sm text-ink-700 sm:grid-cols-3">
                  <p className="flex items-center gap-2">
                    <CalendarDays className="h-4 w-4 text-ink-400" aria-hidden="true" />
                    {formatDate(appointment.appointmentDate)}
                  </p>
                  <p className="flex items-center gap-2">
                    <Clock className="h-4 w-4 text-ink-400" aria-hidden="true" />
                    {appointment.startTime} – {appointment.endTime}
                  </p>
                  <p className="flex items-center gap-2">
                    <DollarSign className="h-4 w-4 text-ink-400" aria-hidden="true" />
                    {formatCurrency(appointment.consultationFeeSnapshot)}
                  </p>
                </div>

                {appointment.status === 'CANCELLED' && appointment.cancellationReason && (
                  <p className="mt-3 flex items-start gap-2 rounded-field bg-danger-100/60 p-2.5 text-sm text-danger-700">
                    <XCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
                    {appointment.cancellationReason}
                  </p>
                )}
              </LinkCard>
            </li>
          ))}
        </ul>
      )}
      </div>
    </AppShell>
  );
}
