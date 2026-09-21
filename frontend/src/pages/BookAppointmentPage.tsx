import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useCreateAppointment, useSpecialties, useDoctors, useDoctorAvailability } from '../hooks/useAppointment';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { Input } from '../components/ui/Input';
import { Select } from '../components/ui/Select';
import { toApiError } from '../api/client';
import { Calendar, Stethoscope, AlertCircle, CheckCircle, Clock } from 'lucide-react';

const bookingSchema = z.object({
  // UI-only field: narrows the doctor dropdown. Never sent to the backend.
  specialtyId: z.number({ error: 'Select a specialty' }).min(1, 'Select a specialty'),
  doctorId: z.number({ error: 'Select a doctor' }).min(1, 'Select a doctor'),
  appointmentDate: z.string().min(1, 'Date is required'),
  startTime: z.string().min(1, 'Start time is required'),
  endTime: z.string().min(1, 'End time is required'),
  reason: z.string().max(500, 'Reason must be at most 500 characters').optional(),
}).refine((data) => data.startTime < data.endTime, {
  message: 'End time must be after start time',
  path: ['endTime'],
});

type BookingFormData = z.infer<typeof bookingSchema>;

const DAY_NAMES = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];

/**
 * Patient booking page (/appointments/new).
 *
 * The patient is never asked for their own ID: the backend books the appointment
 * for the signed-in account's patient profile (JWT identity). The form only picks
 * specialty → doctor → date → time. Availability shown here is guidance from
 * GET /doctors/{id}/availability; the backend re-validates everything on submit.
 */
export function BookAppointmentPage() {
  const navigate = useNavigate();
  const createMutation = useCreateAppointment();

  const specialtiesQuery = useSpecialties();
  const {
    register,
    handleSubmit,
    watch,
    resetField,
    formState: { errors },
  } = useForm<BookingFormData>({
    resolver: zodResolver(bookingSchema),
    defaultValues: {
      specialtyId: undefined,
      doctorId: undefined,
      appointmentDate: '',
      startTime: '',
      endTime: '',
      reason: '',
    },
  });

  const specialtyId = watch('specialtyId');
  const doctorId = watch('doctorId');
  const appointmentDate = watch('appointmentDate');
  const startTime = watch('startTime');
  const endTime = watch('endTime');

  const doctorsQuery = useDoctors(specialtyId ?? null);
  const availabilityQuery = useDoctorAvailability(doctorId ?? null, appointmentDate || null);

  // Changing the specialty invalidates the previously chosen doctor.
  useEffect(() => {
    resetField('doctorId');
  }, [specialtyId, resetField]);

  // Changing doctor or date invalidates previously chosen times.
  useEffect(() => {
    resetField('startTime');
    resetField('endTime');
  }, [doctorId, appointmentDate, resetField]);

  const selectedDoctor = doctorsQuery.data?.find((d) => d.id === doctorId);

  const onSubmit = async (data: BookingFormData) => {
    try {
      const response = await createMutation.mutateAsync({
        doctorId: data.doctorId,
        appointmentDate: data.appointmentDate,
        startTime: data.startTime,
        endTime: data.endTime,
        reason: data.reason || undefined,
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
            <Button variant="outline" onClick={() => navigate('/appointments')}>
              My Appointments
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
            The appointment is booked for your own patient profile — the one linked to your
            sign-in. No IDs to type.
          </p>
        </div>

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div className="md:col-span-2">
              <h2 className="text-lg font-medium text-gray-900 mb-4 flex items-center gap-2">
                <Stethoscope className="w-5 h-5" />
                Choose a Doctor
              </h2>
            </div>

            <Select
              label="Specialty"
              error={errors.specialtyId?.message}
              disabled={specialtiesQuery.isLoading}
              {...register('specialtyId', { valueAsNumber: true })}
            >
              <option value="">Select a specialty...</option>
              {specialtiesQuery.data?.map((s) => (
                <option key={s.id} value={s.id}>{s.name}</option>
              ))}
            </Select>

            <Select
              label="Doctor"
              error={errors.doctorId?.message}
              disabled={!specialtyId || doctorsQuery.isLoading}
              {...register('doctorId', { valueAsNumber: true })}
            >
              <option value="">
                {specialtyId ? 'Select a doctor...' : 'Select a specialty first'}
              </option>
              {doctorsQuery.data?.map((d) => (
                <option key={d.id} value={d.id}>
                  Dr. {d.firstName} {d.lastName}
                </option>
              ))}
            </Select>

            {selectedDoctor && (
              <div className="md:col-span-2 p-4 bg-blue-50 border border-blue-100 rounded-lg text-sm text-gray-700">
                <span className="font-medium text-gray-900">
                  Dr. {selectedDoctor.firstName} {selectedDoctor.lastName}
                </span>{' '}
                · {selectedDoctor.specialty} · {selectedDoctor.yearsExperience} yrs experience ·
                {' '}Fee: ${selectedDoctor.consultationFee}
                {selectedDoctor.bio && <div className="mt-1 text-gray-600">{selectedDoctor.bio}</div>}
              </div>
            )}
          </div>

          <div className="border-t border-gray-200 my-2" />

          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div className="md:col-span-2">
              <h2 className="text-lg font-medium text-gray-900 mb-4 flex items-center gap-2">
                <Clock className="w-5 h-5" />
                Pick a Time
              </h2>
            </div>

            <Input
              label="Appointment Date"
              type="date"
              {...register('appointmentDate')}
              error={errors.appointmentDate?.message}
            />

            <div className="md:col-span-2">
              {doctorId && appointmentDate && (
                availabilityQuery.isLoading ? (
                  <p className="text-sm text-gray-500">Loading availability...</p>
                ) : availabilityQuery.data && availabilityQuery.data.length > 0 ? (
                  <div>
                    <p className="text-sm font-medium text-gray-700 mb-2">
                      Available windows on {appointmentDate}:
                    </p>
                    <div className="flex flex-wrap gap-2">
                      {availabilityQuery.data.map((w, i) => (
                        <span
                          key={i}
                          className="px-3 py-1 bg-green-50 border border-green-200 text-green-800 rounded-full text-sm"
                        >
                          {DAY_NAMES[w.dayOfWeek]} {w.startTime}–{w.endTime}
                        </span>
                      ))}
                    </div>
                  </div>
                ) : (
                  <p className="text-sm text-amber-700 bg-amber-50 border border-amber-200 rounded-lg p-3">
                    No availability windows for this doctor on {appointmentDate}. Pick another date —
                    the doctor does not take appointments that day.
                  </p>
                )
              )}
            </div>

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

            <div className="md:col-span-2">
              <Input
                label="Reason for visit (optional)"
                type="text"
                placeholder="e.g. chest pain during exercise"
                {...register('reason')}
                error={errors.reason?.message}
              />
            </div>

            {selectedDoctor && appointmentDate && startTime && endTime && (
              <div className="md:col-span-2 p-4 bg-gray-50 border border-gray-200 rounded-lg text-sm">
                <p className="font-medium text-gray-900 mb-1">Booking summary</p>
                <p className="text-gray-700">
                  Dr. {selectedDoctor.firstName} {selectedDoctor.lastName} ({selectedDoctor.specialty})
                  {' '}on {appointmentDate}, {startTime}–{endTime} · Fee: ${selectedDoctor.consultationFee}
                </p>
              </div>
            )}
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
