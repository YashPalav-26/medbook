import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useCreateAppointment, useSpecialties, useDoctors, useDoctorAvailability } from '../hooks/useAppointment';
import { AppShell } from '../components/layout/AppShell';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { Input } from '../components/ui/Input';
import { Select } from '../components/ui/Select';
import { ErrorBox, Skeleton, Spinner } from '../components/ui/Feedback';
import { useToast } from '../components/ui/Toast';
import { toApiError } from '../api/client';
import { Calendar, Stethoscope, CheckCircle2, Clock, User } from 'lucide-react';

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

/** Wizard step indicator: shows where the patient is and what is completed. */
function Stepper({ current }: { current: 1 | 2 | 3 }) {
  const steps = [
    { n: 1, label: 'Doctor' },
    { n: 2, label: 'Date & time' },
    { n: 3, label: 'Confirm' },
  ];
  return (
    <ol aria-label="Booking progress" className="mb-8 flex items-center gap-2">
      {steps.map((step, i) => {
        const state = step.n < current ? 'done' : step.n === current ? 'active' : 'todo';
        return (
          <li key={step.n} className="flex items-center gap-2">
            <span
              aria-current={state === 'active' ? 'step' : undefined}
              className={`inline-flex h-7 w-7 items-center justify-center rounded-full text-sm font-semibold ${
                state === 'done'
                  ? 'bg-brand-100 text-brand-700'
                  : state === 'active'
                    ? 'bg-brand-600 text-white'
                    : 'bg-ink-100 text-ink-400'
              }`}
            >
              {state === 'done' ? <CheckCircle2 className="h-4 w-4" aria-hidden="true" /> : step.n}
            </span>
            <span className={`text-sm font-medium ${state === 'todo' ? 'text-ink-400' : 'text-ink-800'}`}>
              {step.label}
            </span>
            {i < steps.length - 1 && <span aria-hidden="true" className="mx-1 h-px w-6 bg-ink-200 sm:w-10" />}
          </li>
        );
      })}
    </ol>
  );
}

/**
 * Patient booking page (/appointments/new), styled as a guided flow:
 * Step 1 specialty + doctor (cards), Step 2 date + availability + time,
 * Step 3 confirmation summary before submit. Identity stays JWT-derived —
 * no patient ID anywhere. All backend rules untouched.
 */
export function BookAppointmentPage() {
  const navigate = useNavigate();
  const toast = useToast();
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

  useEffect(() => {
    resetField('doctorId');
  }, [specialtyId, resetField]);

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
      toast.success('Appointment booked.');
      navigate(`/appointments/${response.id}`);
    } catch {
      // Error handled by mutation state below.
    }
  };

  const apiError = createMutation.error ? toApiError(createMutation.error) : null;

  // Derive the current wizard step from how far the patient has gotten.
  const step: 1 | 2 | 3 = !doctorId ? 1 : !startTime ? 2 : 3;

  return (
    <AppShell title="Book an Appointment">
      <Stepper current={step} />

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
        {/* Step 1 — specialty and doctor */}
        <section aria-labelledby="step-doctor">
          <h2 id="step-doctor" className="mb-4 flex items-center gap-2 font-medium text-ink-900">
            <Stethoscope className="h-5 w-5 text-brand-600" aria-hidden="true" />
            Choose a doctor
          </h2>
          <Card className="space-y-4 p-5">
            <div className="grid gap-4 sm:grid-cols-2">
              <Select
                label="Specialty"
                helper="Doctors of this specialty appear below."
                error={errors.specialtyId?.message}
                disabled={specialtiesQuery.isLoading}
                {...register('specialtyId', { valueAsNumber: true })}
              >
                <option value="">Select a specialty…</option>
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
                  {specialtyId ? 'Select a doctor…' : 'Select a specialty first'}
                </option>
                {doctorsQuery.data?.map((d) => (
                  <option key={d.id} value={d.id}>
                    Dr. {d.firstName} {d.lastName}
                  </option>
                ))}
              </Select>
            </div>

            {/* Doctor card — real fields only */}
            {doctorsQuery.isLoading && specialtyId ? (
              <div className="space-y-2">
                <Skeleton className="h-4 w-48" />
                <Skeleton className="h-4 w-64" />
              </div>
            ) : selectedDoctor ? (
              <div className="rounded-field border border-brand-200 bg-brand-50 p-4">
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <div className="flex items-center gap-3">
                    <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-white text-brand-600 shadow-card">
                      <User className="h-5 w-5" aria-hidden="true" />
                    </div>
                    <div>
                      <p className="font-medium text-ink-900">
                        Dr. {selectedDoctor.firstName} {selectedDoctor.lastName}
                      </p>
                      <p className="text-sm text-ink-500">
                        {selectedDoctor.specialty} · {selectedDoctor.yearsExperience} yrs experience
                      </p>
                    </div>
                  </div>
                  <p className="font-display text-xl font-semibold text-brand-700">
                    ${selectedDoctor.consultationFee}
                  </p>
                </div>
                {selectedDoctor.bio && (
                  <p className="mt-3 text-sm text-ink-600">{selectedDoctor.bio}</p>
                )}
              </div>
            ) : null}
          </Card>
        </section>

        {/* Step 2 — date and time */}
        <section aria-labelledby="step-time">
          <h2 id="step-time" className="mb-4 flex items-center gap-2 font-medium text-ink-900">
            <Clock className="h-5 w-5 text-brand-600" aria-hidden="true" />
            Pick a date and time
          </h2>
          <Card className="space-y-4 p-5">
            <Input
              label="Appointment date"
              type="date"
              className="sm:max-w-xs"
              {...register('appointmentDate')}
              error={errors.appointmentDate?.message}
            />

            {doctorId && appointmentDate && (
              availabilityQuery.isLoading ? (
                <div className="flex gap-2">
                  <Skeleton className="h-8 w-32 rounded-full" />
                  <Skeleton className="h-8 w-40 rounded-full" />
                </div>
              ) : availabilityQuery.data && availabilityQuery.data.length > 0 ? (
                <div>
                  <p className="text-sm font-medium text-ink-700">
                    Availability on {appointmentDate}:
                  </p>
                  <div className="mt-2 flex flex-wrap gap-2">
                    {availabilityQuery.data.map((w, i) => (
                      <span
                        key={i}
                        className="rounded-full bg-success-100 px-3 py-1 text-sm text-success-700"
                      >
                        {DAY_NAMES[w.dayOfWeek]} {w.startTime.slice(0, 5)}–{w.endTime.slice(0, 5)}
                      </span>
                    ))}
                  </div>
                </div>
              ) : (
                <p className="rounded-field border border-warning-100 bg-warning-100/60 p-3 text-sm text-warning-700">
                  No availability windows for this doctor on {appointmentDate}. Try another date —
                  the doctor does not take appointments that day.
                </p>
              )
            )}

            <div className="grid gap-4 sm:grid-cols-2">
              <Input label="Start time" type="time" {...register('startTime')} error={errors.startTime?.message} />
              <Input label="End time" type="time" {...register('endTime')} error={errors.endTime?.message} />
            </div>
          </Card>
        </section>

        {/* Step 3 — confirmation summary */}
        <section aria-labelledby="step-confirm">
          <h2 id="step-confirm" className="mb-4 flex items-center gap-2 font-medium text-ink-900">
            <Calendar className="h-5 w-5 text-brand-600" aria-hidden="true" />
            Confirm your visit
          </h2>
          <Card className="p-5">
            {selectedDoctor && appointmentDate && startTime && endTime ? (
              <div className="space-y-2 text-sm">
                <p className="text-ink-700">
                  <span className="font-medium text-ink-900">
                    Dr. {selectedDoctor.firstName} {selectedDoctor.lastName}
                  </span>{' '}
                  ({selectedDoctor.specialty})
                </p>
                <p className="text-ink-700">{appointmentDate} · {startTime}–{endTime}</p>
                <p className="text-ink-700">Consultation fee: ${selectedDoctor.consultationFee}</p>
                <Input
                  label="Reason for visit (optional)"
                  type="text"
                  placeholder="e.g. chest pain during exercise"
                  {...register('reason')}
                  error={errors.reason?.message}
                />
              </div>
            ) : (
              <p className="text-sm text-ink-500">
                Choose a specialty, doctor, date and time above — your booking summary will appear here.
              </p>
            )}

            {apiError && <div className="mt-4"><ErrorBox message={apiError.message} /></div>}

            <div className="mt-5 flex flex-wrap gap-3">
              <Button type="submit" disabled={createMutation.isPending} size="lg">
                {createMutation.isPending && <Spinner className="text-white" />}
                {createMutation.isPending ? 'Booking…' : 'Book Appointment'}
              </Button>
              <Button type="button" variant="ghost" onClick={() => navigate('/dashboard')}>
                Cancel
              </Button>
            </div>
          </Card>
        </section>
      </form>
    </AppShell>
  );
}
