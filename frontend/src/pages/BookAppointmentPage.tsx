import { useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useCreateAppointment } from '../hooks/useAppointment';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { Input } from '../components/ui/Input';
import { toApiError } from '../api/client';
import { Calendar, User, Stethoscope, AlertCircle, CheckCircle } from 'lucide-react';

const appointmentSchema = z.object({
  // Zod v4: an empty number input coerces to NaN → invalid_type error (message below);
  // .min(1) then rejects zero/negative values.
  patientId: z.number({ error: 'Patient ID is required' }).min(1, 'Patient ID is required'),
  doctorId: z.number({ error: 'Doctor ID is required' }).min(1, 'Doctor ID is required'),
  appointmentDate: z.string().min(1, 'Date is required'),
  startTime: z.string().min(1, 'Start time is required'),
  endTime: z.string().min(1, 'End time is required'),
}).refine((data) => {
  return data.startTime < data.endTime;
}, {
  message: 'End time must be after start time',
  path: ['endTime'],
});

type AppointmentFormData = z.infer<typeof appointmentSchema>;

export function BookAppointmentPage() {
  const navigate = useNavigate();
  const createMutation = useCreateAppointment();

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<AppointmentFormData>({
    resolver: zodResolver(appointmentSchema),
    // No silent ID substitution: the operator types the real patient/doctor IDs and
    // exactly those values are submitted to the backend.
    defaultValues: {
      patientId: undefined,
      doctorId: undefined,
      appointmentDate: '',
      startTime: '',
      endTime: '',
    },
  });

  const onSubmit = async (data: AppointmentFormData) => {
    try {
      const response = await createMutation.mutateAsync({
        patientId: data.patientId,
        doctorId: data.doctorId,
        appointmentDate: data.appointmentDate,
        startTime: data.startTime,
        endTime: data.endTime,
      });
      navigate(`/appointments/${response.id}`);
    } catch {
      // Error handled by mutation state
    }
  };

  const apiError = createMutation.error ? toApiError(createMutation.error) : null;

  if (createMutation.isSuccess) {
    return (
      <div className="min-h-screen bg-gray-50 py-12 px-4">
        <Card className="max-w-md mx-auto p-8">
          <div className="text-center">
            <div className="w-16 h-16 bg-green-100 rounded-full flex items-center justify-center mx-auto mb-4">
              <CheckCircle className="w-8 h-8 text-green-600" />
            </div>
            <h2 className="text-2xl font-bold text-gray-900 mb-2">Appointment Booked!</h2>
            <p className="text-gray-600 mb-6">Your appointment has been successfully created.</p>
            <Button onClick={() => navigate('/appointments/new')} className="mr-4">
              Book Another
            </Button>
            <Button variant="outline" onClick={() => navigate('/dashboard')}>
              Back to Dashboard
            </Button>
          </div>
        </Card>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50 py-12 px-4">
      <Card className="max-w-2xl mx-auto p-8">
        <div className="mb-8">
          <h1 className="text-2xl font-bold text-gray-900 flex items-center gap-3">
            <Calendar className="w-8 h-8 text-blue-600" />
            Book an Appointment
          </h1>
          <p className="text-gray-600 mt-2">
            Fill in the details below to schedule a new appointment.
          </p>
        </div>

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div className="md:col-span-2">
              <h2 className="text-lg font-medium text-gray-900 mb-4 flex items-center gap-2">
                <User className="w-5 h-5" />
                Patient Information
              </h2>
            </div>
            <Input
              label="Patient ID"
              type="number"
              {...register('patientId', { valueAsNumber: true })}
              error={errors.patientId?.message}
              placeholder="Enter your patient ID"
            />
          </div>

          <div className="border-t border-gray-200 my-6" />

          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div className="md:col-span-2">
              <h2 className="text-lg font-medium text-gray-900 mb-4 flex items-center gap-2">
                <Stethoscope className="w-5 h-5" />
                Appointment Details
              </h2>
            </div>
            <Input
              label="Doctor ID"
              type="number"
              {...register('doctorId', { valueAsNumber: true })}
              error={errors.doctorId?.message}
              placeholder="Enter the doctor's ID"
            />
            <Input
              label="Appointment Date"
              type="date"
              {...register('appointmentDate')}
              error={errors.appointmentDate?.message}
            />
            <Input
              label="Start Time"
              type="time"
              {...register('startTime')}
              error={errors.startTime?.message}
            />
            <Input
              label="End Time"
              type="time"
              {...register('endTime')}
              error={errors.endTime?.message}
            />
          </div>

          {apiError && (
            <div className="p-4 bg-red-50 border border-red-200 rounded-lg">
              <div className="flex items-center gap-2 text-red-700 font-medium mb-2">
                <AlertCircle className="w-5 h-5" />
                {apiError.message}
              </div>
              {apiError.fieldErrors.length > 0 && (
                <ul className="list-disc list-inside text-sm text-red-600">
                  {apiError.fieldErrors.map((fe, i) => (
                    <li key={i}>{fe.field}: {fe.message}</li>
                  ))}
                </ul>
              )}
            </div>
          )}

          <div className="flex gap-4 pt-4">
            <Button
              type="submit"
              disabled={createMutation.isPending}
              className="flex-1"
            >
              {createMutation.isPending ? 'Booking...' : 'Book Appointment'}
            </Button>
            <Button
              type="button"
              variant="outline"
              onClick={() => navigate('/dashboard')}
            >
              Cancel
            </Button>
          </div>
        </form>
      </Card>
    </div>
  );
}