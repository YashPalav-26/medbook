import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { useMyAppointments } from '../hooks/useMyAppointments';
import { AppShell } from '../components/layout/AppShell';
import { Brand } from '../components/ui/Brand';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { StatusBadge, EmptyState, Skeleton } from '../components/ui/Feedback';
import { CalendarPlus, CalendarDays, ArrowRight, Clock, Stethoscope } from 'lucide-react';

/**
 * Patient dashboard. Personalized welcome, quick actions, and the next upcoming
 * appointment pulled from the real /appointments/me data (no fabricated stats).
 * The upcoming card links into the full appointment list.
 */
export function DashboardPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const appointmentsQuery = useMyAppointments();

  const appointments = appointmentsQuery.data ?? [];
  const upcoming = appointments
    .filter((a) => a.status === 'SCHEDULED' || a.status === 'CONFIRMED')
    .sort((a, b) => (a.appointmentDate + a.startTime).localeCompare(b.appointmentDate + b.startTime));
  const next = upcoming[0];

  return (
    <AppShell>
      {/* Welcome */}
      <div className="mb-8 flex flex-wrap items-end justify-between gap-4">
        <div>
          <h2 className="font-display text-3xl font-semibold text-ink-900">
            Good to see you, {user?.firstName ?? 'there'}
          </h2>
          <p className="mt-1 text-ink-500">
            {upcoming.length > 0
              ? `You have ${upcoming.length} upcoming appointment${upcoming.length > 1 ? 's' : ''}.`
              : 'No upcoming appointments right now.'}
          </p>
        </div>
        <Link to="/appointments/new">
          <Button size="lg">
            <CalendarPlus className="h-4 w-4" aria-hidden="true" />
            Book Appointment
          </Button>
        </Link>
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        {/* Next appointment — real data or a real empty state */}
        <Card className="p-6 lg:col-span-2">
          <div className="mb-4 flex items-center justify-between">
            <h3 className="font-medium text-ink-900">Upcoming appointment</h3>
            <Link
              to="/appointments/me"
              className="inline-flex items-center gap-1 text-sm font-medium text-brand-700 hover:underline"
            >
              View all
              <ArrowRight className="h-3.5 w-3.5" aria-hidden="true" />
            </Link>
          </div>

          {appointmentsQuery.isLoading ? (
            <div className="space-y-3">
              <Skeleton className="h-5 w-40" />
              <Skeleton className="h-4 w-64" />
              <Skeleton className="h-9 w-48" />
            </div>
          ) : appointmentsQuery.isError ? (
            <EmptyState
              title="Could not load your appointments"
              message="Check your connection and try again."
              action={<Button variant="outline" size="sm" onClick={() => appointmentsQuery.refetch()}>Retry</Button>}
            />
          ) : next ? (
            <div>
              <div className="flex flex-wrap items-center gap-3">
                <span className="font-display text-2xl font-semibold text-ink-900">
                  Dr. visit #{next.id}
                </span>
                <StatusBadge status={next.status} />
              </div>
              <div className="mt-4 grid gap-3 sm:grid-cols-2">
                <div className="flex items-center gap-3 rounded-field border border-ink-100 bg-ink-50 p-3">
                  <Clock className="h-5 w-5 text-brand-600" aria-hidden="true" />
                  <div>
                    <p className="text-sm font-medium text-ink-900">{next.appointmentDate}</p>
                    <p className="text-sm text-ink-500">{next.startTime} – {next.endTime}</p>
                  </div>
                </div>
                <div className="flex items-center gap-3 rounded-field border border-ink-100 bg-ink-50 p-3">
                  <Stethoscope className="h-5 w-5 text-brand-600" aria-hidden="true" />
                  <div>
                    <p className="text-sm font-medium text-ink-900">Doctor #{next.doctorId}</p>
                    <p className="text-sm text-ink-500">Fee ${next.consultationFeeSnapshot}</p>
                  </div>
                </div>
              </div>
              <Button variant="outline" className="mt-4" onClick={() => navigate(`/appointments/${next.id}`)}>
                View details
              </Button>
            </div>
          ) : (
            <EmptyState
              icon={<CalendarDays className="h-8 w-8" />}
              title="Nothing scheduled yet"
              message="When you book a visit, it will show up here with its time and doctor."
              action={<Link to="/appointments/new"><Button size="sm">Book your first appointment</Button></Link>}
            />
          )}
        </Card>

        {/* Quick actions + profile summary */}
        <div className="space-y-6">
          <Card hover className="p-6" onClick={() => navigate('/appointments/me')}>
            <div className="flex items-center gap-4">
              <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-brand-50 text-brand-600">
                <CalendarDays className="h-5 w-5" aria-hidden="true" />
              </div>
              <div>
                <h3 className="font-medium text-ink-900">My Appointments</h3>
                <p className="text-sm text-ink-500">Review past and upcoming visits</p>
              </div>
            </div>
          </Card>

          <Card className="p-6">
            <h3 className="mb-3 font-medium text-ink-900">Your details</h3>
            <dl className="space-y-2 text-sm">
              <div className="flex justify-between gap-4">
                <dt className="text-ink-500">Name</dt>
                <dd className="font-medium text-ink-800">{user?.firstName} {user?.lastName}</dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-ink-500">Email</dt>
                <dd className="truncate font-medium text-ink-800">{user?.email}</dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-ink-500">Role</dt>
                <dd className="font-medium text-ink-800">{user?.roles?.[0]?.replace('ROLE_', '') ?? '—'}</dd>
              </div>
            </dl>
          </Card>
        </div>
      </div>

      {/* Hidden brand reference keeps the wordmark visible for screen-reader users on this route too */}
      <span className="sr-only"><Brand /></span>
    </AppShell>
  );
}
