import { useParams, useNavigate, Link } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import * as z from 'zod';
import * as React from 'react';
import { useAppointment, useCancelAppointment } from '../hooks/useAppointment';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { Input } from '../components/ui/Input';
import { toApiError } from '../api/client';
import {
  Calendar,
  Clock,
  User,
  Stethoscope,
  AlertCircle,
  XCircle,
  CheckCircle,
  Loader2,
  IndianRupee
} from 'lucide-react';
import type { AppointmentStatus } from '../types/appointment';

const cancelSchema = z.object({
  cancellationReason: z.string().min(1, 'Cancellation reason is required').max(255, 'Reason must be at most 255 characters'),
});

type CancelFormData = z.infer<typeof cancelSchema>;

const statusColors: Record<AppointmentStatus, string> = {
  SCHEDULED: 'bg-blue-100 text-blue-800',
  CONFIRMED: 'bg-green-100 text-green-800',
  COMPLETED: 'bg-gray-100 text-gray-800',
  CANCELLED: 'bg-red-100 text-red-800',
  NO_SHOW: 'bg-orange-100 text-orange-800',
};

const statusIcons: Record<AppointmentStatus, React.ReactElement> = {
  SCHEDULED: <Clock className="w-4 h-4" />,
  CONFIRMED: <CheckCircle className="w-4 h-4" />,
  COMPLETED: <CheckCircle className="w-4 h-4" />,
  CANCELLED: <XCircle className="w-4 h-4" />,
  NO_SHOW: <AlertCircle className="w-4 h-4" />,
};

export function AppointmentDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [showCancelForm, setShowCancelForm] = React.useState(false);

  const appointmentId = id ? parseInt(id, 10) : null;
  const { data: appointment, isLoading, error } = useAppointment(appointmentId);
  const cancelMutation = useCancelAppointment();

  const {
    register,
    handleSubmit,
    formState: { errors },
    reset,
  } = useForm<CancelFormData>({
    resolver: zodResolver(cancelSchema),
  });

  const onCancelSubmit = async (data: CancelFormData) => {
    if (!appointmentId) return;

    try {
      await cancelMutation.mutateAsync({
        id: appointmentId,
        data: { cancellationReason: data.cancellationReason },
      });
      setShowCancelForm(false);
      reset();
    } catch (err) {
      // Error handled by mutation
    }
  };

  const apiError = error ? toApiError(error) : null;
  const cancelError = cancelMutation.error ? toApiError(cancelMutation.error) : null;

  if (isLoading) {
    return (
      <div className="min-h-screen bg-gray-50 py-12 px-4 flex items-center justify-center">
        <div className="flex items-center gap-3 text-gray-600">
          <Loader2 className="w-6 h-6 animate-spin" />
          Loading appointment...
        </div>
      </div>
    );
  }

  if (apiError?.status === 404 || !appointment) {
    return (
      <div className="min-h-screen bg-gray-50 py-12 px-4">
        <Card className="max-w-md mx-auto p-8">
          <div className="text-center">
            <AlertCircle className="w-16 h-16 text-red-600 mx-auto mb-4" />
            <h2 className="text-2xl font-bold text-gray-900 mb-2">Appointment Not Found</h2>
            <p className="text-gray-600 mb-6">
              {apiError?.message || 'The appointment you are looking for does not exist.'}
            </p>
            <Button onClick={() => navigate('/dashboard')}>Back to Dashboard</Button>
          </div>
        </Card>
      </div>
    );
  }

  if (apiError) {
    return (
      <div className="min-h-screen bg-gray-50 py-12 px-4">
        <Card className="max-w-md mx-auto p-8">
          <div className="text-center">
            <AlertCircle className="w-16 h-16 text-red-600 mx-auto mb-4" />
            <h2 className="text-2xl font-bold text-gray-900 mb-2">Error Loading Appointment</h2>
            <p className="text-gray-600 mb-6">{apiError.message}</p>
            <Button onClick={() => navigate('/dashboard')}>Back to Dashboard</Button>
          </div>
        </Card>
      </div>
    );
  }

  const canCancel = appointment.status === 'SCHEDULED' || appointment.status === 'CONFIRMED';

  return (
    <div className="min-h-screen bg-gray-50 py-12 px-4">
      <div className="max-w-3xl mx-auto">
        <div className="mb-6">
          <Link to="/dashboard" className="text-blue-600 hover:underline text-sm">
            ← Back to Dashboard
          </Link>
        </div>

        <Card className="p-8">
          <div className="flex items-start justify-between mb-6">
            <div>
              <h1 className="text-2xl font-bold text-gray-900 flex items-center gap-3">
                <Calendar className="w-8 h-8 text-blue-600" />
                Appointment #{appointment.id}
              </h1>
            </div>
            <div className={`px-3 py-1 rounded-full text-sm font-medium flex items-center gap-2 ${statusColors[appointment.status]}`}>
              {statusIcons[appointment.status]}
              {appointment.status}
            </div>
          </div>

          <div className="space-y-6">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div>
                <h2 className="text-sm font-medium text-gray-500 mb-2 flex items-center gap-2">
                  <User className="w-4 h-4" />
                  Patient
                </h2>
                <p className="text-lg text-gray-900">Patient ID: {appointment.patientId}</p>
              </div>

              <div>
                <h2 className="text-sm font-medium text-gray-500 mb-2 flex items-center gap-2">
                  <Stethoscope className="w-4 h-4" />
                  Doctor
                </h2>
                <p className="text-lg text-gray-900">Doctor ID: {appointment.doctorId}</p>
              </div>
            </div>

            <div className="border-t border-gray-200 pt-6">
              <h2 className="text-sm font-medium text-gray-500 mb-4 flex items-center gap-2">
                <Calendar className="w-4 h-4" />
                Appointment Details
              </h2>
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                <div>
                  <p className="text-sm text-gray-500">Date</p>
                  <p className="text-lg font-medium text-gray-900">{appointment.appointmentDate}</p>
                </div>
                <div>
                  <p className="text-sm text-gray-500">Start Time</p>
                  <p className="text-lg font-medium text-gray-900">{appointment.startTime}</p>
                </div>
                <div>
                  <p className="text-sm text-gray-500">End Time</p>
                  <p className="text-lg font-medium text-gray-900">{appointment.endTime}</p>
                </div>
              </div>
            </div>

            <div className="border-t border-gray-200 pt-6">
              <h2 className="text-sm font-medium text-gray-500 mb-2 flex items-center gap-2">
                <IndianRupee className="w-4 h-4" />
                Consultation Fee
              </h2>
              <p className="text-2xl font-bold text-gray-900">₹{appointment.consultationFeeSnapshot.toFixed(2)}</p>
            </div>

            {appointment.cancellationReason && (
              <div className="border-t border-gray-200 pt-6">
                <h2 className="text-sm font-medium text-gray-500 mb-2 flex items-center gap-2">
                  <XCircle className="w-4 h-4" />
                  Cancellation Reason
                </h2>
                <p className="text-gray-900">{appointment.cancellationReason}</p>
              </div>
            )}

            {canCancel && !showCancelForm && (
              <div className="border-t border-gray-200 pt-6">
                <Button
                  variant="outline"
                  onClick={() => setShowCancelForm(true)}
                  className="text-red-600 border-red-300 hover:bg-red-50"
                >
                  Cancel Appointment
                </Button>
              </div>
            )}

            {canCancel && showCancelForm && (
              <div className="border-t border-gray-200 pt-6">
                <h2 className="text-lg font-medium text-gray-900 mb-4">Cancel Appointment</h2>
                <form onSubmit={handleSubmit(onCancelSubmit)} className="space-y-4">
                  <Input
                    label="Cancellation Reason"
                    {...register('cancellationReason')}
                    error={errors.cancellationReason?.message}
                    placeholder="Please provide a reason for cancellation"
                  />

                  {cancelError && (
                    <div className="p-3 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm flex items-center gap-2">
                      <AlertCircle className="w-4 h-4" />
                      {cancelError.message}
                    </div>
                  )}

                  <div className="flex gap-4">
                    <Button
                      type="submit"
                      disabled={cancelMutation.isPending}
                      className="bg-red-600 hover:bg-red-700 text-white"
                    >
                      {cancelMutation.isPending ? 'Cancelling...' : 'Confirm Cancellation'}
                    </Button>
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
                  </div>
                </form>
              </div>
            )}
          </div>
        </Card>
      </div>
    </div>
  );
}