import { useParams, useNavigate, Link } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import * as z from 'zod';
import * as React from 'react';
import { useAuth } from '../hooks/useAuth';
import {
  useAppointment,
  useCancelAppointment,
  useConfirmAppointment,
  useCompleteAppointment,
  useNoShowAppointment,
} from '../hooks/useAppointment';
import { AppShell } from '../components/layout/AppShell';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { Input } from '../components/ui/Input';
import { Modal } from '../components/ui/Modal';
import { StatusBadge, ErrorBox, EmptyState, Skeleton, Spinner } from '../components/ui/Feedback';
import { useToast } from '../components/ui/Toast';
import { toApiError } from '../api/client';
import {
  Calendar,
  Clock,
  User,
  Stethoscope,
  AlertCircle,
  XCircle,
  IndianRupee,
  CheckCircle2,
  BadgeCheck,
  UserX,
} from 'lucide-react';

const cancelSchema = z.object({
  cancellationReason: z.string().min(1, 'Cancellation reason is required').max(255, 'Reason must be at most 255 characters'),
});

type CancelFormData = z.infer<typeof cancelSchema>;

export function AppointmentDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [showCancelForm, setShowCancelForm] = React.useState(false);

  const appointmentId = id ? parseInt(id, 10) : null;
  const { data: appointment, isLoading, error } = useAppointment(appointmentId);
  const cancelMutation = useCancelAppointment();

  // Doctor lifecycle actions (server still enforces role + ownership + transition rules;
  // these buttons are convenience, not security).
  const { user } = useAuth();
  const isDoctor = user?.roles?.includes('ROLE_DOCTOR') ?? false;
  const confirmMutation = useConfirmAppointment();
  const completeMutation = useCompleteAppointment();
  const noShowMutation = useNoShowAppointment();
  const [pendingAction, setPendingAction] = React.useState<'confirm' | 'complete' | 'no-show' | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors },
    reset,
  } = useForm<CancelFormData>({
    resolver: zodResolver(cancelSchema),
  });

  const toast = useToast();

  const onCancelSubmit = async (data: CancelFormData) => {
    if (!appointmentId) return;

    try {
      await cancelMutation.mutateAsync({
        id: appointmentId,
        data: { cancellationReason: data.cancellationReason },
      });
      setShowCancelForm(false);
      reset();
      toast.success('Appointment cancelled.');
    } catch {
      // Error surfaced through the modal via cancelMutation.error.
    }
  };

  const apiError = error ? toApiError(error) : null;
  const cancelError = cancelMutation.error ? toApiError(cancelMutation.error) : null;

  const doctorActions = {
    confirm: {
      open: 'Confirm this appointment?',
      description: 'The patient will see this visit as confirmed.',
      button: 'Confirm Appointment',
      success: 'Appointment confirmed.',
      mutation: confirmMutation,
    },
    complete: {
      open: 'Mark this visit as completed?',
      description: 'This closes the appointment as COMPLETED. A completed visit cannot be changed afterwards.',
      button: 'Mark Completed',
      success: 'Visit marked as completed.',
      mutation: completeMutation,
    },
    'no-show': {
      open: 'Mark this appointment as no-show?',
      description: 'Use this when the patient did not arrive. The appointment is closed as NO_SHOW.',
      button: 'Mark No-Show',
      success: 'Appointment marked as no-show.',
      mutation: noShowMutation,
    },
  } as const;

  const onDoctorAction = async () => {
    if (!appointmentId || !pendingAction) return;
    try {
      await doctorActions[pendingAction].mutation.mutateAsync(appointmentId);
      toast.success(doctorActions[pendingAction].success);
      setPendingAction(null);
    } catch {
      // Error surfaced through the modal via the mutation's error state.
    }
  };

  const actionMeta = pendingAction ? doctorActions[pendingAction] : null;
  const actionError = actionMeta?.mutation.error ? toApiError(actionMeta.mutation.error) : null;

  if (isLoading) {
    return (
      <AppShell title="Appointment">
        <Card className="space-y-4 p-6">
          <div className="flex justify-between">
            <Skeleton className="h-7 w-40" />
            <Skeleton className="h-6 w-24 rounded-full" />
          </div>
          <Skeleton className="h-4 w-64" />
          <Skeleton className="h-4 w-52" />
          <Skeleton className="h-9 w-36" />
        </Card>
      </AppShell>
    );
  }

  if (apiError?.status === 404 || !appointment) {
    return (
      <AppShell title="Appointment">
        <Card className="p-6">
          <EmptyState
            icon={<AlertCircle className="h-8 w-8" />}
            title="Appointment not found"
            message={apiError?.message || 'The appointment you are looking for does not exist or belongs to another patient.'}
            action={<Button onClick={() => navigate('/dashboard')}>Back to Dashboard</Button>}
          />
        </Card>
      </AppShell>
    );
  }

  if (apiError) {
    return (
      <AppShell title="Appointment">
        <ErrorBox message={apiError.message} onRetry={() => navigate(0)} />
      </AppShell>
    );
  }

  const canCancel = appointment.status === 'SCHEDULED' || appointment.status === 'CONFIRMED';

  return (
    <AppShell title={`Appointment #${appointment.id}`}>
      <div className="mb-6">
        <Link to="/appointments/me" className="inline-flex items-center gap-1 text-sm font-medium text-brand-700 hover:underline">
          <Calendar className="h-4 w-4" aria-hidden="true" />
          Back to My Appointments
        </Link>
      </div>

      <Card className="p-6 sm:p-8">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <h2 className="font-display text-2xl font-semibold text-ink-900">Appointment #{appointment.id}</h2>
            <p className="mt-1 text-sm text-ink-500">Booked visit details and status.</p>
          </div>
          <StatusBadge status={appointment.status} />
        </div>

        <div className="mt-8 space-y-6">
          {/* Who — patient and doctor, straight from the API response */}
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="flex items-center gap-3 rounded-field border border-ink-100 bg-ink-50 p-4">
              <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-white text-brand-600 shadow-card">
                <User className="h-5 w-5" aria-hidden="true" />
              </div>
              <div>
                <p className="text-sm text-ink-500">Patient</p>
                <p className="font-medium text-ink-900">Patient #{appointment.patientId}</p>
              </div>
            </div>
            <div className="flex items-center gap-3 rounded-field border border-ink-100 bg-ink-50 p-4">
              <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-white text-brand-600 shadow-card">
                <Stethoscope className="h-5 w-5" aria-hidden="true" />
              </div>
              <div>
                <p className="text-sm text-ink-500">Doctor</p>
                <p className="font-medium text-ink-900">Doctor #{appointment.doctorId}</p>
              </div>
            </div>
          </div>

          {/* When */}
          <div>
            <h3 className="mb-3 text-sm font-medium text-ink-500">Date &amp; time</h3>
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
              <div className="rounded-field border border-ink-100 p-3">
                <p className="flex items-center gap-1.5 text-xs text-ink-500">
                  <Calendar className="h-3.5 w-3.5" aria-hidden="true" /> Date
                </p>
                <p className="mt-0.5 font-medium text-ink-900">{appointment.appointmentDate}</p>
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
          </div>

          {/* Fee */}
          <div className="flex items-center justify-between rounded-field border border-brand-100 bg-brand-50 p-4">
            <p className="flex items-center gap-2 text-sm font-medium text-ink-700">
              <IndianRupee className="h-4 w-4 text-brand-600" aria-hidden="true" />
              Consultation fee
            </p>
            <p className="font-display text-2xl font-semibold text-brand-700">
              ${appointment.consultationFeeSnapshot.toFixed(2)}
            </p>
          </div>

          {appointment.cancellationReason && (
            <div className="rounded-field border border-danger-600/30 bg-danger-100/60 p-4">
              <p className="flex items-center gap-2 text-sm font-medium text-danger-700">
                <XCircle className="h-4 w-4" aria-hidden="true" />
                Cancellation reason
              </p>
              <p className="mt-1 text-sm text-danger-700">{appointment.cancellationReason}</p>
            </div>
          )}

          {/* Actions — role-aware. Doctors drive the visit lifecycle; patients cancel.
              Every action re-validates on the server (role, ownership, transition). */}
          <div className="flex flex-wrap gap-3">
            {isDoctor && appointment.status === 'SCHEDULED' && (
              <Button onClick={() => setPendingAction('confirm')}>
                <CheckCircle2 className="h-4 w-4" aria-hidden="true" />
                Confirm Appointment
              </Button>
            )}
            {isDoctor && appointment.status === 'CONFIRMED' && (
              <>
                <Button onClick={() => setPendingAction('complete')}>
                  <BadgeCheck className="h-4 w-4" aria-hidden="true" />
                  Mark Completed
                </Button>
                <Button variant="outline" onClick={() => setPendingAction('no-show')}>
                  <UserX className="h-4 w-4" aria-hidden="true" />
                  Mark No-Show
                </Button>
              </>
            )}
            {canCancel && (
              <Button
                variant="outline"
                onClick={() => {
                  reset();
                  setShowCancelForm(true);
                }}
                className="border-danger-300 text-danger-700 hover:bg-danger-100/60"
              >
                <XCircle className="h-4 w-4" aria-hidden="true" />
                Cancel Appointment
              </Button>
            )}
          </div>
        </div>
      </Card>

      {/* Cancel confirmation dialog: warns, requires a reason, shows the consequence */}
      {showCancelForm && (
        <Modal
          title="Cancel this appointment?"
          description="This releases the time slot. The appointment cannot be re-opened afterwards."
          onClose={() => setShowCancelForm(false)}
        >
          <form onSubmit={handleSubmit(onCancelSubmit)} className="space-y-4">
            <Input
              label="Cancellation reason"
              {...register('cancellationReason')}
              error={errors.cancellationReason?.message}
              placeholder="Please provide a reason for cancellation"
            />

            {cancelError && <ErrorBox message={cancelError.message} />}

            <div className="flex justify-end gap-3">
              <Button
                type="button"
                variant="outline"
                onClick={() => {
                  setShowCancelForm(false);
                  reset();
                }}
              >
                Keep Appointment
              </Button>
              <Button
                type="submit"
                disabled={cancelMutation.isPending}
                className="bg-danger-600 hover:bg-danger-700"
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

      {/* Doctor lifecycle confirmation dialog — one reusable modal for all three actions */}
      {actionMeta && (
        <Modal
          title={actionMeta.open}
          description={actionMeta.description}
          onClose={() => setPendingAction(null)}
        >
          <div className="space-y-4">
            {actionError && <ErrorBox message={actionError.message} />}
            <div className="flex justify-end gap-3">
              <Button type="button" variant="outline" onClick={() => setPendingAction(null)}>
                Go Back
              </Button>
              <Button onClick={onDoctorAction} disabled={actionMeta.mutation.isPending}>
                {actionMeta.mutation.isPending ? (
                  <>
                    <Spinner className="text-white" />
                    Updating…
                  </>
                ) : (
                  actionMeta.button
                )}
              </Button>
            </div>
          </div>
        </Modal>
      )}
    </AppShell>
  );
}