import { Link } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { useMyAppointments } from '../hooks/useMyAppointments';
import { AppShell } from '../components/layout/AppShell';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { StatusBadge, EmptyState, Skeleton } from '../components/ui/Feedback';
import { CalendarDays, ArrowRight, Clock, User, Stethoscope } from 'lucide-react';
import { formatDate, todayIso } from '../lib/dates';

/**
 * Doctor dashboard. Real data only: today's and upcoming visits come from the
 * role-aware /appointments/me endpoint (the backend returns appointments assigned
 * to the authenticated doctor). No fabricated KPIs, charts or patient counts.
 */
export function DoctorDashboardPage() {
  const { user } = useAuth();
  const appointmentsQuery = useMyAppointments();

  const appointments = appointmentsQuery.data ?? [];
  const today = todayIso();
  const todays = appointments
    .filter((a) => a.appointmentDate === today)
    .sort((a, b) => a.startTime.localeCompare(b.startTime));
  const upcoming = appointments
    .filter((a) => (a.status === 'SCHEDULED' || a.status === 'CONFIRMED') && a.appointmentDate > today)
    .sort((a, b) => (a.appointmentDate + a.startTime).localeCompare(b.appointmentDate + b.startTime));

  return (
    <AppShell title="Doctor Dashboard">
      {/* Welcome */}
      <div className="mb-8">
        <h2 className="font-display text-3xl font-semibold text-ink-900">
          Welcome back, Dr. {user?.lastName ?? 'there'}
        </h2>
        <p className="mt-1 text-ink-500">
          {todays.length > 0
            ? `You have ${todays.length} appointment${todays.length > 1 ? 's' : ''} today.`
            : 'No appointments scheduled for today.'}
        </p>
      </div>

      {appointmentsQuery.isLoading ? (
        <div className="space-y-6">
          {[0, 1].map((i) => (
            <Card key={i} className="space-y-3 p-6">
              <Skeleton className="h-5 w-36" />
              <Skeleton className="h-4 w-64" />
              <Skeleton className="h-4 w-52" />
            </Card>
          ))}
        </div>
      ) : appointmentsQuery.isError ? (
        <Card className="p-6">
          <EmptyState
            icon={<Stethoscope className="h-8 w-8" />}
            title="Could not load your schedule"
            message="Check your connection and try again."
            action={<Button variant="outline" onClick={() => appointmentsQuery.refetch()}>Retry</Button>}
          />
        </Card>
      ) : (
        <div className="space-y-6">
          {/* Today — the working list for the day */}
          <Card className="p-6">
            <div className="mb-4 flex items-center justify-between">
              <h3 className="font-medium text-ink-900">Today&rsquo;s appointments</h3>
              <Link
                to="/doctor/appointments"
                className="inline-flex items-center gap-1 text-sm font-medium text-brand-700 hover:underline"
              >
                View all
                <ArrowRight className="h-3.5 w-3.5" aria-hidden="true" />
              </Link>
            </div>

            {todays.length === 0 ? (
              <EmptyState
                icon={<CalendarDays className="h-8 w-8" />}
                title="Nothing booked for today"
                message="Appointments assigned to you by patients will appear here on the day of the visit."
              />
            ) : (
              <ul className="divide-y divide-ink-100">
                {todays.map((a) => (
                  <li key={a.id}>
                    <Link
                      to={`/appointments/${a.id}`}
                      className="flex flex-wrap items-center justify-between gap-3 py-3 transition-colors hover:bg-ink-50"
                    >
                      <div className="flex items-center gap-3">
                        <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-brand-50 text-brand-600">
                          <Clock className="h-4 w-4" aria-hidden="true" />
                        </div>
                        <div>
                          <p className="text-sm font-medium text-ink-900">
                            {a.startTime} – {a.endTime}
                          </p>
                          <p className="text-sm text-ink-500">{a.patientName}</p>
                        </div>
                      </div>
                      <StatusBadge status={a.status} />
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </Card>

          {/* Upcoming — beyond today */}
          <Card className="p-6">
            <h3 className="mb-4 font-medium text-ink-900">Upcoming</h3>
            {upcoming.length === 0 ? (
              <EmptyState
                icon={<CalendarDays className="h-8 w-8" />}
                title="No upcoming appointments"
                message="Future confirmed and scheduled visits will appear here."
              />
            ) : (
              <ul className="divide-y divide-ink-100">
                {upcoming.slice(0, 5).map((a) => (
                  <li key={a.id}>
                    <Link
                      to={`/appointments/${a.id}`}
                      className="flex flex-wrap items-center justify-between gap-3 py-3 transition-colors hover:bg-ink-50"
                    >
                      <div className="flex items-center gap-3">
                        <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-brand-50 text-brand-600">
                          <User className="h-4 w-4" aria-hidden="true" />
                        </div>
                        <div>
                          <p className="text-sm font-medium text-ink-900">
                            {formatDate(a.appointmentDate)} · {a.startTime} – {a.endTime}
                          </p>
                          <p className="text-sm text-ink-500">{a.patientName}</p>
                        </div>
                      </div>
                      <StatusBadge status={a.status} />
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </Card>
        </div>
      )}
    </AppShell>
  );
}
