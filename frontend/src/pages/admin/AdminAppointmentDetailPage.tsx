import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useAdminAppointment, useAdminCancelAppointment } from '../../hooks/useAdmin';
import { toApiError } from '../../api/client';
import { AppShell } from '../../components/layout/AppShell';
import { PageHeader } from '../../components/admin/AdminUi';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { Input } from '../../components/ui/Input';
import { Modal } from '../../components/ui/Modal';
import { StatusBadge, EmptyState, ErrorBox, Skeleton, Spinner } from '../../components/ui/Feedback';
import { toast } from '../../lib/toast';
import { formatDate, formatDateTime } from '../../lib/dates';
import { formatCurrency } from '../../lib/format';
import {
  Calendar, CalendarSearch, Clock, DollarSign, Stethoscope, User, XCircle,
  AlertCircle, History,
} from 'lucide-react';

/**
 * ADMIN appointment detail (GET /admin/appointments/:id): full visit summary with
 * patient/doctor names and specialty, the status-change audit timeline, and the one
 * explicitly implemented admin action — support cancellation with a required reason.
 *
 * Admins never confirm/complete/no-show (that stays the assigned doctor's workflow);
 * the cancel button only appears for SCHEDULED/CONFIRMED rows, mirroring the backend rule.
 */

const cancelSchema = z.object({
  cancellationReason: z
    .string()
    .min(1, 'Cancellation reason is required')
    .max(255, 'Reason must be at most 255 characters'),
});
type CancelFormData = z.infer<typeof cancelSchema>;

/** One timeline entry: status pill, actor, note, timestamp. */
function HistoryItem({
  status,
  changedByName,
  notes,
  changedAt,
  isLast,
}: {
  status: string;
  changedByName: string | null;
  notes: string | null;
  changedAt: string;
  isLast: boolean;
}) {
  return (
    <li className="relative flex gap-4 pb-6 last:pb-0">
      {!isLast && <span aria-hidden="true" className="absolute left-[7px] top-5 h-full w-0.5 bg-ink-100" />}
      <span
        aria-hidden="true"
        className="relative mt-1.5 h-3.5 w-3.5 shrink-0 rounded-full border-2 border-brand-500 bg-white"
      />
      <div className="min-w-0">
        <div className="flex flex-wrap items-center gap-2">
          <StatusBadge status={status} />
          <span className="text-sm text-ink-500">{formatDateTime(changedAt)}</span>
        </div>
        {notes && <p className="mt-1 break-words text-sm text-ink-700">{notes}</p>}
        <p className="mt-0.5 text-xs text-ink-400">
          {changedByName ? `by ${changedByName}` : 'by system (no user recorded)'}
        </p>
      </div>
    </li>
  );
}

export function AdminAppointmentDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [showCancelModal, setShowCancelModal] = useState(false);

  const appointmentId = id && Number.isInteger(Number(id)) ? Number(id) : null;
  const { data: detail, isLoading, isError, error, refetch } = useAdminAppointment(appointmentId);
  const cancelMutation = useAdminCancelAppointment();

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<CancelFormData>({
    resolver: zodResolver(cancelSchema),
    defaultValues: { cancellationReason: '' },
  });

  // Toasts come from the Sonner-based facade (lib/toast) — no provider needed.

  const onCancelSubmit = async (data: CancelFormData) => {
    if (!appointmentId) return;
    try {
      await cancelMutation.mutateAsync({ id: appointmentId, reason: data.cancellationReason });
      setShowCancelModal(false);
      reset();
      toast.success('Appointment cancelled. The change is recorded in the audit trail.');
    } catch {
      // Error surfaced inside the modal via cancelMutation.error.
    }
  };

  const apiError = isError ? toApiError(error) : null;
  const cancelError = cancelMutation.error ? toApiError(cancelMutation.error) : null;

  if (isLoading) {
    return (
      <AppShell title="Appointment">
        <Card className="space-y-4 p-6">
          <Skeleton className="h-7 w-40" />
          <Skeleton className="h-4 w-64" />
          <Skeleton className="h-4 w-52" />
        </Card>
      </AppShell>
    );
  }

  if (apiError?.status === 404) {
    return (
      <AppShell title="Appointment">
        <Card className="p-6">
          <EmptyState
            icon={<AlertCircle className="h-8 w-8" />}
            title="Appointment not found"
            message="This appointment does not exist. It may have been removed, or the link is wrong."
            action={<Button onClick={() => navigate('/admin/appointments')}>Back to Appointments</Button>}
          />
        </Card>
      </AppShell>
    );
  }

  if (apiError) {
    return (
      <AppShell title="Appointment">
        <ErrorBox
          message={apiError.status === 403 ? 'You are not allowed to view this appointment.' : apiError.message}
          onRetry={() => refetch()}
        />
      </AppShell>
    );
  }

  if (!detail) return null;

  const { appointment, history } = detail;
  const canCancel = appointment.status === 'SCHEDULED' || appointment.status === 'CONFIRMED';

  return (
    <AppShell title="Appointment">
      <div className="mx-auto max-w-4xl">
        <div className="mb-6">
          <Link
            to="/admin/appointments"
            className="inline-flex items-center gap-1 text-sm font-medium text-brand-700 hover:text-brand-800 hover:underline"
          >
            <CalendarSearch className="h-4 w-4" aria-hidden="true" />
            Back to Appointments
          </Link>
        </div>

        <PageHeader
          icon={<CalendarSearch className="h-6 w-6 text-brand-600" />}
          title={`Appointment #${appointment.id}`}
          description="Support view: participants, visit details, audit trail."
        />

        <div className="space-y-6">
          {/* Summary */}
          <Card className="p-6 sm:p-8">
            <div className="flex flex-wrap items-start justify-between gap-4">
              <h2 className="font-display text-xl font-semibold text-ink-900">Visit details</h2>
              <StatusBadge status={appointment.status} />
            </div>

            <div className="mt-6 space-y-6">
              {/* Who */}
              <div className="grid gap-4 sm:grid-cols-2">
                <div className="flex items-center gap-3 rounded-field border border-ink-100 bg-ink-50 p-4">
                  <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-white text-brand-600 shadow-card">
                    <User className="h-5 w-5" aria-hidden="true" />
                  </div>
                  <div>
                    <p className="text-sm text-ink-500">Patient</p>
                    <p className="font-medium text-ink-900">
                      {appointment.patientName} <span className="text-ink-400">#{appointment.patientId}</span>
                    </p>
                  </div>
                </div>
                <div className="flex items-center gap-3 rounded-field border border-ink-100 bg-ink-50 p-4">
                  <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-white text-brand-600 shadow-card">
                    <Stethoscope className="h-5 w-5" aria-hidden="true" />
                  </div>
                  <div>
                    <p className="text-sm text-ink-500">Doctor</p>
                    <p className="font-medium text-ink-900">
                      Dr. {appointment.doctorName} <span className="text-ink-400">#{appointment.doctorId}</span>
                    </p>
                    <p className="text-xs text-ink-500">{appointment.specialty}</p>
                  </div>
                </div>
              </div>

              {/* When */}
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
                <div className="rounded-field border border-ink-100 p-3">
                  <p className="flex items-center gap-1.5 text-xs text-ink-500">
                    <Calendar className="h-3.5 w-3.5" aria-hidden="true" /> Date
                  </p>
                  <p className="mt-0.5 font-medium text-ink-900">{formatDate(appointment.appointmentDate)}</p>
                </div>
                <div className="rounded-field border border-ink-100 p-3">
                  <p className="flex items-center gap-1.5 text-xs text-ink-500">
                    <Clock className="h-3.5 w-3.5" aria-hidden="true" /> Start
                  </p>
                  <p className="mt-0.5 font-medium text-ink-900">{appointment.startTime}</p>
                </div>
                <div className="rounded-field border border-ink-100 p-3">
                  <p className="flex items-center gap-1.5 text-xs text-ink-500">
                    <Clock className="h-3.5 w-3.5" aria-hidden="true" /> End
                  </p>
                  <p className="mt-0.5 font-medium text-ink-900">{appointment.endTime}</p>
                </div>
              </div>

              {/* Fee */}
              <div className="flex items-center justify-between rounded-field border border-brand-100 bg-brand-50 p-4">
                <p className="flex items-center gap-2 text-sm font-medium text-ink-700">
                  <DollarSign className="h-4 w-4 text-brand-600" aria-hidden="true" />
                  Consultation fee (locked at booking)
                </p>
                <p className="font-display text-2xl font-semibold text-brand-700">
                  {formatCurrency(appointment.consultationFeeSnapshot)}
                </p>
              </div>

              {/* Cancellation info */}
              {appointment.cancellationReason && (
                <div className="rounded-field border border-danger-600/30 bg-danger-100/60 p-4">
                  <p className="flex items-center gap-2 text-sm font-medium text-danger-700">
                    <XCircle className="h-4 w-4" aria-hidden="true" />
                    Cancellation reason
                  </p>
                  <p className="mt-1 text-sm text-danger-700">{appointment.cancellationReason}</p>
                </div>
              )}

              {/* The one admin action: support cancellation with a required reason */}
              {canCancel && (
                <div className="flex flex-wrap gap-3">
                  <Button
                    variant="dangerOutline"
                    onClick={() => {
                      reset();
                      setShowCancelModal(true);
                    }}
                  >
                    <XCircle className="h-4 w-4" aria-hidden="true" />
                    Cancel as Admin
                  </Button>
                </div>
              )}
            </div>
          </Card>

          {/* Status history timeline */}
          <Card className="p-6 sm:p-8">
            <h2 className="flex items-center gap-2 font-display text-xl font-semibold text-ink-900">
              <History className="h-5 w-5 text-ink-500" aria-hidden="true" />
              Status history
            </h2>
            <p className="mt-1 text-sm text-ink-500">
              Every status change is recorded with the user who made it (audit trail).
            </p>
            {history.length === 0 ? (
              <EmptyState message="No status changes have been recorded yet." />
            ) : (
              <ul className="mt-6">
                {history.map((entry, index) => (
                  <HistoryItem
                    key={entry.id}
                    status={entry.status}
                    changedByName={entry.changedByName}
                    notes={entry.notes}
                    changedAt={entry.changedAt}
                    isLast={index === history.length - 1}
                  />
                ))}
              </ul>
            )}
          </Card>
        </div>
      </div>

      {/* Cancel confirmation: warns, requires a reason, shows the consequence */}
      {showCancelModal && (
        <Modal
          title="Cancel this appointment as admin?"
          description="This releases the time slot and records you as the actor in the audit trail. The appointment cannot be re-opened afterwards."
          onClose={() => setShowCancelModal(false)}
        >
          <form onSubmit={handleSubmit(onCancelSubmit)} className="flex flex-col gap-4">
            <Input
              label="Cancellation reason"
              {...register('cancellationReason')}
              error={errors.cancellationReason?.message}
              placeholder="e.g. Patient contacted support to reschedule"
            />
            {cancelError && <ErrorBox message={cancelError.message} />}
            <div className="flex justify-end gap-3">
              <Button
                type="button"
                variant="outline"
                onClick={() => {
                  setShowCancelModal(false);
                  reset();
                }}
              >
                Keep Appointment
              </Button>
              <Button
                type="submit"
                variant="danger"
                disabled={cancelMutation.isPending}
              >
                {cancelMutation.isPending ? (
                  <>
                    <Spinner className="text-white" />
                    Cancelling…
                  </>
                ) : (
                  'Confirm Cancellation'
                )}
              </Button>
            </div>
          </form>
        </Modal>
      )}
    </AppShell>
  );
}
