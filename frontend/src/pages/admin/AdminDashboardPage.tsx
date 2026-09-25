import { Link } from 'react-router-dom';
import { Card } from '../../components/ui/Card';
import { AdminNav } from './SpecialtiesAdminPage';
import { PageHeader, StatusBadge, ErrorBox, Skeleton } from '../../components/admin/AdminUi';
import { useAdminOverview } from '../../hooks/useAdmin';
import { toApiError } from '../../api/client';
import {
  Stethoscope, UserCog, CalendarClock, CalendarSearch, TrendingUp, Activity,
} from 'lucide-react';
import type { AppointmentStatus } from '../../types/appointment';

/**
 * ADMIN dashboard — the entry point of the admin area.
 *
 * The overview section shows only metrics the backend actually computes from live
 * appointment rows (GET /admin/appointments/overview): today's appointments, the
 * per-status breakdown and the all-time total. No revenue, growth, percentages or
 * any other number the system cannot derive — fake stats are worse than none.
 */
export function AdminDashboardPage() {
  const { data: overview, isLoading, isError, error, refetch } = useAdminOverview();

  const statusRows: { status: AppointmentStatus; count: number }[] = overview
    ? (Object.entries(overview.statusCounts) as [AppointmentStatus, number][]).map(
        ([status, count]) => ({ status, count }),
      )
    : [];

  return (
    <div className="min-h-screen bg-gray-50">
      <AdminNav />
      <main className="max-w-4xl mx-auto px-6 py-8">
        <PageHeader
          icon={<UserCog className="w-8 h-8 text-blue-600" />}
          title="Admin Dashboard"
          description="Clinic overview and management of the reference data patients and doctors use every day."
        />

        {/* Real clinic overview — every figure computed from live appointment rows */}
        <section aria-label="Clinic overview" className="mb-8">
          <h2 className="mb-3 flex items-center gap-2 text-sm font-semibold uppercase tracking-wider text-ink-400">
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
                    Today ({overview.today})
                  </p>
                  <p className="mt-1 font-display text-3xl font-semibold text-brand-700">
                    {overview.todayAppointments}
                  </p>
                  <p className="text-xs text-ink-500">appointments booked</p>
                </div>
                <div className="rounded-field border border-ink-100 p-4">
                  <p className="text-xs font-medium uppercase tracking-wide text-ink-500">Total</p>
                  <p className="mt-1 font-display text-3xl font-semibold text-ink-900">
                    {overview.totalAppointments}
                  </p>
                  <p className="text-xs text-ink-500">appointments all-time</p>
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

        {/* Management cards */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          <Link to="/admin/appointments" className="block">
            <Card className="p-6 flex flex-col items-center text-center space-y-3 hover:shadow-md transition-shadow cursor-pointer">
              <div className="w-12 h-12 bg-amber-100 rounded-full flex items-center justify-center text-amber-600">
                <CalendarSearch className="w-6 h-6" />
              </div>
              <h3 className="font-medium text-gray-900">Manage Appointments</h3>
              <p className="text-sm text-gray-600">Review, filter and support clinic visits</p>
            </Card>
          </Link>
          <Link to="/admin/specialties" className="block">
            <Card className="p-6 flex flex-col items-center text-center space-y-3 hover:shadow-md transition-shadow cursor-pointer">
              <div className="w-12 h-12 bg-blue-100 rounded-full flex items-center justify-center text-blue-600">
                <Stethoscope className="w-6 h-6" />
              </div>
              <h3 className="font-medium text-gray-900">Manage Specialties</h3>
              <p className="text-sm text-gray-600">Add or retire medical specialties</p>
            </Card>
          </Link>
          <Link to="/admin/doctors" className="block">
            <Card className="p-6 flex flex-col items-center text-center space-y-3 hover:shadow-md transition-shadow cursor-pointer">
              <div className="w-12 h-12 bg-green-100 rounded-full flex items-center justify-center text-green-600">
                <UserCog className="w-6 h-6" />
              </div>
              <h3 className="font-medium text-gray-900">Manage Doctors</h3>
              <p className="text-sm text-gray-600">Create profiles and login accounts</p>
            </Card>
          </Link>
          <Link to="/admin/availability" className="block">
            <Card className="p-6 flex flex-col items-center text-center space-y-3 hover:shadow-md transition-shadow cursor-pointer">
              <div className="w-12 h-12 bg-purple-100 rounded-full flex items-center justify-center text-purple-600">
                <CalendarClock className="w-6 h-6" />
              </div>
              <h3 className="font-medium text-gray-900">Manage Availability</h3>
              <p className="text-sm text-gray-600">Set when doctors take appointments</p>
            </Card>
          </Link>
          <Link to="/dashboard" className="block">
            <Card className="p-6 flex flex-col items-center text-center space-y-3 hover:shadow-md transition-shadow cursor-pointer">
              <div className="w-12 h-12 bg-ink-100 rounded-full flex items-center justify-center text-ink-600">
                <TrendingUp className="w-6 h-6" />
              </div>
              <h3 className="font-medium text-gray-900">Patient Area</h3>
              <p className="text-sm text-gray-600">Open your own patient dashboard</p>
            </Card>
          </Link>
        </div>
      </main>
    </div>
  );
}
