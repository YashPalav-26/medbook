import { useRef, useState } from 'react';
import { useMyAppointments } from '../hooks/useMyAppointments';
import { toApiError } from '../api/client';
import { AppShell } from '../components/layout/AppShell';
import { Button } from '../components/ui/Button';
import { Card, LinkCard } from '../components/ui/Card';
import { StatusBadge, EmptyState, ErrorBox, Skeleton } from '../components/ui/Feedback';
import { CalendarDays, Clock, DollarSign, User, XCircle } from 'lucide-react';
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

function labelFor(filter: StatusFilter): string {
  return FILTERS.find((f) => f.key === filter)?.label ?? 'matching';
}

export function DoctorAppointmentsPage() {
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
      <div className="mb-6">
        <p className="text-ink-500">
          Appointments patients have booked with you, with their current status.
        </p>
      </div>

      {/* Status filter tabs */}
      <div className="mb-6 flex flex-wrap gap-2" role="tablist" aria-label="Filter appointments by status">
        {FILTERS.map(({ key, label }, index) => (
          <button
            key={key}
            ref={(element) => { tabRefs.current[index] = element; }}
            type="button"
            role="tab"
            id={`doctor-appointments-tab-${key}`}
            aria-selected={filter === key}
            aria-controls="doctor-appointments-panel"
            tabIndex={filter === key ? 0 : -1}
            onClick={() => setFilter(key)}
            onKeyDown={(event) => onTabKeyDown(event, index)}
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

      <div
        id="doctor-appointments-panel"
        role="tabpanel"
        aria-labelledby={`doctor-appointments-tab-${filter}`}
      >
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
              <LinkCard to={`/appointments/${appointment.id}`} className="p-5">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="flex items-center gap-4">
                    <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-brand-50 text-brand-600">
                      <User className="h-5 w-5" aria-hidden="true" />
                    </div>
                    <div>
                      <p className="font-medium text-ink-900">{appointment.patientName}</p>
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
