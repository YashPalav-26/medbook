import { Card, LinkCard } from '../../components/ui/Card';
import { AppShell } from '../../components/layout/AppShell';
import { useAuth } from '../../hooks/useAuth';
import { PageHeader, StatusBadge, ErrorBox, Skeleton } from '../../components/admin/AdminUi';
import { useAdminOverview } from '../../hooks/useAdmin';
import { toApiError } from '../../api/client';
import { formatDate } from '../../lib/dates';
import {
  Stethoscope, UserCog, CalendarClock, CalendarSearch, TrendingUp, Activity,
} from 'lucide-react';
import type { AppointmentStatus } from '../../types/appointment';
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid, Cell } from 'recharts';

/** Same semantic colors as StatusBadge, one per appointment status. */
const STATUS_BAR_COLORS: Record<AppointmentStatus, string> = {
  SCHEDULED: '#38bdf8',     // info
  CONFIRMED: '#16a34a',     // success green (matches the Confirmed chip)
  COMPLETED: '#6b7280',     // ink-500
  CANCELLED: '#dc2626',     // danger-600
  NO_SHOW:   '#d97706',     // warning-600
};

/** Management destinations — one array so the cards cannot drift apart. */
const MANAGEMENT_LINKS = [
  {
    to: '/admin/appointments',
    icon: CalendarSearch,
    title: 'Manage Appointments',
    description: 'Review, filter and support clinic visits',
  },
  {
    to: '/admin/specialties',
    icon: Stethoscope,
    title: 'Manage Specialties',
    description: 'Add or retire medical specialties',
  },
  {
    to: '/admin/doctors',
    icon: UserCog,
    title: 'Manage Doctors',
    description: 'Create profiles and login accounts',
  },
  {
    to: '/admin/availability',
    icon: CalendarClock,
    title: 'Manage Availability',
    description: 'Set when doctors take appointments',
  },
  {
    to: '/dashboard',
    icon: TrendingUp,
    title: 'Patient Area',
    description: 'Open your own patient dashboard',
  },
];

/**
 * ADMIN dashboard — the entry point of the admin area.
 *
 * The overview section shows only metrics the backend actually computes from live
 * appointment rows (GET /admin/appointments/overview): today's appointments, the
 * per-status breakdown and the all-time total. No revenue, growth, percentages or
 * any other number the system cannot derive — fake stats are worse than none.
 */
export function AdminDashboardPage() {
  const { user } = useAuth();
  // The patient dashboard is only reachable/useful with the PATIENT role.
  const isPatient = user?.roles?.includes('ROLE_PATIENT') ?? false;
  const managementLinks = MANAGEMENT_LINKS.filter(
    (link) => link.to !== '/dashboard' || isPatient,
  );
  const { data: overview, isLoading, isError, error, refetch } = useAdminOverview();

  const statusRows: { status: AppointmentStatus; count: number }[] = overview
    ? (Object.entries(overview.statusCounts) as [AppointmentStatus, number][]).map(
        ([status, count]) => ({ status, count }),
      )
    : [];

  return (
    <AppShell>
      <div className="mx-auto max-w-4xl">
        <PageHeader
          icon={<UserCog className="h-6 w-6" aria-hidden="true" />}
          title="Admin Dashboard"
          description="Clinic overview and management of the reference data patients and doctors use every day."
        />

        {/* Real clinic overview — every figure computed from live appointment rows */}
        <section aria-labelledby="clinic-overview" className="mb-8">
          <h2 id="clinic-overview" className="mb-3 flex items-center gap-2 text-sm font-semibold uppercase tracking-wider text-ink-400">
            <Activity className="h-4 w-4" aria-hidden="true" />
            Clinic overview
          </h2>
          {isLoading ? (
            <Card className="space-y-3 p-6">
              <Skeleton className="h-5 w-48" />
              <Skeleton className="h-4 w-full" />
              <Skeleton className="h-4 w-2/3" />
            </Card>
          ) : isError ? (
            <ErrorBox
              message={
                toApiError(error).status === 403
                  ? 'You are not allowed to view the clinic overview.'
                  : toApiError(error).message
              }
              onRetry={() => refetch()}
            />
          ) : overview ? (
            <Card className="p-6">
              <div className="grid grid-cols-2 gap-4 sm:grid-cols-3">
                <div className="rounded-field border border-brand-100 bg-brand-50 p-4">
                  <p className="text-xs font-medium uppercase tracking-wide text-ink-500">
                    {formatDate(overview.today)}
                  </p>
                  <p className="mt-1 font-display text-3xl font-semibold text-brand-700">
                    {overview.todayAppointments}
                  </p>
                  <p className="text-xs text-ink-500">appointments today</p>
                </div>
                <div className="rounded-field border border-ink-100 p-4">
                  <p className="text-xs font-medium uppercase tracking-wide text-ink-500">All time</p>
                  <p className="mt-1 font-display text-3xl font-semibold text-ink-900">
                    {overview.totalAppointments}
                  </p>
                  <p className="text-xs text-ink-500">appointments booked</p>
                </div>
                <div className="col-span-2 sm:col-span-1">
                  <ul className="space-y-2">
                    {statusRows.map(({ status, count }) => (
                      <li key={status} className="flex items-center justify-between gap-2">
                        <StatusBadge status={status} />
                        <span className="font-medium text-ink-900">{count}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              </div>
            </Card>
          ) : null}
        </section>

        {/* Appointments by status — real data from the overview endpoint. The per-status
            numbers are already listed above with words, so the chart is decorative for
            assistive tech rather than a colour-only source of truth. */}
        {overview && (
          <section aria-labelledby="status-chart" className="mb-8">
            <h2 id="status-chart" className="mb-3 flex items-center gap-2 text-sm font-semibold uppercase tracking-wider text-ink-400">
              <TrendingUp className="h-4 w-4" aria-hidden="true" />
              Appointments by status
            </h2>
            <Card className="p-6">
              <div className="h-56 w-full" aria-hidden="true">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={statusRows} margin={{ top: 8, right: 8, bottom: 0, left: -16 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#eceff0" vertical={false} />
                    <XAxis
                      dataKey="status"
                      tickFormatter={(value: AppointmentStatus) => value.replace('_', ' ')}
                      tick={{ fontSize: 11, fill: '#677d85' }}
                      tickLine={false}
                      axisLine={{ stroke: '#eceff0' }}
                      interval={0}
                    />
                    <YAxis allowDecimals={false} tick={{ fontSize: 11, fill: '#677d85' }} tickLine={false} axisLine={false} />
                    <Tooltip
                      cursor={{ fill: 'rgba(28,36,39,0.04)' }}
                      formatter={(value) => [value, 'Appointments']}
                      labelFormatter={(label) => String(label).replace('_', ' ')}
                    />
                    <Bar dataKey="count" radius={[4, 4, 0, 0]} maxBarSize={56} isAnimationActive={false}>
                      {statusRows.map(({ status }) => (
                        <Cell key={status} fill={STATUS_BAR_COLORS[status]} />
                      ))}
                    </Bar>
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </Card>
          </section>
        )}

        {/* Management cards */}
        <section aria-labelledby="management" className="mb-2">
          <h2 id="management" className="mb-3 text-sm font-semibold uppercase tracking-wider text-ink-400">
            Management
          </h2>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {managementLinks.map(({ to, icon: Icon, title, description }) => (
              <LinkCard key={to} to={to} className="p-5">
                <div className="flex items-start gap-3">
                  <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-brand-50 text-brand-600">
                    <Icon className="h-5 w-5" aria-hidden="true" />
                  </div>
                  <div>
                    <h3 className="font-medium text-ink-900">{title}</h3>
                    <p className="mt-0.5 text-sm text-ink-600">{description}</p>
                  </div>
                </div>
              </LinkCard>
            ))}
          </div>
        </section>
      </div>
    </AppShell>
  );
}
