import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useAdminDoctors, useAdminAvailability, useCreateAvailability, useUpdateAvailability, useDeactivateAvailability } from '../../hooks/useAdmin';
import type { DoctorAvailabilityResponse } from '../../types/appointment';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { Input } from '../../components/ui/Input';
import { Select } from '../../components/ui/Select';
import { Modal } from '../../components/ui/Modal';
import { AppShell } from '../../components/layout/AppShell';
import { PageHeader, EmptyState, ErrorBox, Spinner } from '../../components/admin/AdminUi';
import { toApiError } from '../../api/client';
import { formatDate } from '../../lib/dates';
import { CalendarClock, Ban, Pencil, PlusCircle } from 'lucide-react';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Paper from '@mui/material/Paper';
import IconButton from '@mui/material/IconButton';
import Tooltip from '@mui/material/Tooltip';
import { StatusBadge } from '../../components/admin/AdminUi';

const DAYS = [
  { value: 0, label: 'Sunday' },
  { value: 1, label: 'Monday' },
  { value: 2, label: 'Tuesday' },
  { value: 3, label: 'Wednesday' },
  { value: 4, label: 'Thursday' },
  { value: 5, label: 'Friday' },
  { value: 6, label: 'Saturday' },
];

const dayLabel = (value: number) => DAYS.find((d) => d.value === value)?.label ?? String(value);

function isClosed(window: DoctorAvailabilityResponse): boolean {
  if (window.effectiveUntil == null) return false;
  const yesterday = new Date();
  yesterday.setDate(yesterday.getDate() - 1);
  const until = new Date(window.effectiveUntil + 'T00:00:00');
  return until < yesterday;
}

const windowSchema = z.object({
  dayOfWeek: z.number({ error: 'Pick a day' }).min(0).max(6),
  startTime: z.string().min(1, 'Start time is required'),
  endTime: z.string().min(1, 'End time is required'),

  recurringValue: z.enum(['true', 'false']),
  effectiveFrom: z.string().optional(),
  effectiveUntil: z.string().optional(),
}).refine((data) => data.startTime < data.endTime, {
  message: 'Start time must be before end time',
  path: ['endTime'],
}).refine((data) => !data.effectiveFrom || !data.effectiveUntil || data.effectiveFrom <= data.effectiveUntil, {
  message: 'Effective until must not be before effective from',
  path: ['effectiveUntil'],
});

type WindowFormData = z.infer<typeof windowSchema>;

export function AvailabilityAdminPage() {
  const doctorsQuery = useAdminDoctors();
  const [doctorId, setDoctorId] = useState<number | null>(null);
  const availabilityQuery = useAdminAvailability(doctorId);
  const createMutation = useCreateAvailability();
  const updateMutation = useUpdateAvailability();
  const deactivateMutation = useDeactivateAvailability();

  const [editing, setEditing] = useState<DoctorAvailabilityResponse | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [bannerError, setBannerError] = useState<string | null>(null);

  const form = useForm<WindowFormData>({
    resolver: zodResolver(windowSchema),
    defaultValues: { dayOfWeek: undefined, startTime: '', endTime: '', recurringValue: 'true', effectiveFrom: '', effectiveUntil: '' },
  });

  const openCreate = () => {
    setEditing(null);
    form.reset({ dayOfWeek: undefined, startTime: '', endTime: '', recurringValue: 'true', effectiveFrom: '', effectiveUntil: '' });
    setBannerError(null);
    setModalOpen(true);
  };

  const openEdit = (window: DoctorAvailabilityResponse) => {
    setEditing(window);
    form.reset({
      dayOfWeek: window.dayOfWeek,
      startTime: window.startTime.slice(0, 5),
      endTime: window.endTime.slice(0, 5),
      recurringValue: window.recurring ? 'true' : 'false',
      effectiveFrom: window.effectiveFrom ?? '',
      effectiveUntil: window.effectiveUntil ?? '',
    });
    setBannerError(null);
    setModalOpen(true);
  };

  const submit = async (data: WindowFormData) => {
    setBannerError(null);
    if (doctorId === null) return;
    const doctorIdForCall: number = doctorId;
    const payload = {
      ...data,
      recurring: data.recurringValue === 'true',
      effectiveFrom: data.effectiveFrom || undefined,
      effectiveUntil: data.effectiveUntil || undefined,
    };
    try {
      if (editing) {
        await updateMutation.mutateAsync({ id: editing.id as unknown as number, payload });
      } else {
        await createMutation.mutateAsync({ doctorId: doctorIdForCall, payload });
      }
      setModalOpen(false);
    } catch { /* shown in modal */ }
  };

  const modalError = createMutation.error
    ? toApiError(createMutation.error)
    : updateMutation.error
      ? toApiError(updateMutation.error)
      : null;

  const close = async (window: DoctorAvailabilityResponse) => {
    setBannerError(null);
    try {
      await deactivateMutation.mutateAsync(window.id as unknown as number);
    } catch (error) {
      setBannerError(toApiError(error).message);
    }
  };

  const doctors = doctorsQuery.data ?? [];
  const windows = availabilityQuery.data ?? [];
  const pending = createMutation.isPending || updateMutation.isPending;

  return (
    <AppShell>
      <div className="mx-auto max-w-4xl">
        <PageHeader
          icon={<CalendarClock className="h-6 w-6 text-brand-600" />}
          title="Manage Availability"
          description="When doctors can take appointments. Closing a window keeps its history but stops future bookings."
        />

        <Card className="p-6 mb-6">
          <Select
            label="Doctor"
            value={doctorId ?? ''}
            onChange={(e) => {
              setDoctorId(e.target.value ? Number(e.target.value) : null);
              setBannerError(null);
            }}
          >
            <option value="">Select a doctor...</option>
            {doctors.map((d) => (
              <option key={d.id} value={d.id}>Dr. {d.firstName} {d.lastName} — {d.specialty}</option>
            ))}
          </Select>
        </Card>

        {bannerError && <div className="mb-4"><ErrorBox message={bannerError} /></div>}

        {doctorId !== null && (
          <Card className="p-6">
            <div className="flex items-center justify-between mb-4">
              <h3 className="font-medium text-ink-900">Weekly schedule</h3>
              <Button onClick={openCreate} className="flex items-center gap-2">
                <PlusCircle className="w-4 h-4" />
                New Window
              </Button>
            </div>

            {availabilityQuery.isLoading ? (
              <EmptyState loading message="Loading availability..." />
            ) : availabilityQuery.isError ? (
              <ErrorBox message={toApiError(availabilityQuery.error).message} />
            ) : windows.length === 0 ? (
              <EmptyState
                icon={<CalendarClock className="h-6 w-6 text-ink-300" />}
                message="No availability windows for this doctor yet. Add one so patients can book."
              />
            ) : (
              <TableContainer component={Paper} className="overflow-x-auto !bg-transparent !shadow-none">
                <Table size="small" aria-label="Availability windows">
                  <TableHead>
                    <TableRow>
                      <TableCell>Day</TableCell>
                      <TableCell>Time</TableCell>
                      <TableCell>Pattern</TableCell>
                      <TableCell>Effective</TableCell>
                      <TableCell>Status</TableCell>
                      <TableCell align="right">Actions</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {windows.map((window) => {
                      const closed = isClosed(window);
                      return (
                        <TableRow key={window.id ?? `${window.dayOfWeek}-${window.startTime}`} hover>
                          <TableCell className="!font-medium text-ink-900">{dayLabel(window.dayOfWeek)}</TableCell>
                          <TableCell className="text-ink-700">{window.startTime.slice(0, 5)}–{window.endTime.slice(0, 5)}</TableCell>
                          <TableCell className="text-ink-700">{window.recurring ? 'Weekly' : 'One-off'}</TableCell>
                          <TableCell className="text-ink-700">
                            {window.effectiveFrom ? formatDate(window.effectiveFrom) : '—'} →{' '}
                            {window.effectiveUntil ? formatDate(window.effectiveUntil) : 'open ended'}
                          </TableCell>
                          <TableCell>
                            <StatusBadge status={closed ? 'Closed' : 'Open'} />
                          </TableCell>
                          <TableCell align="right" className="whitespace-nowrap">
                            <Tooltip title="Edit window">
                              <IconButton size="small" onClick={() => openEdit(window)} aria-label="Edit window">
                                <Pencil className="w-4 h-4" />
                              </IconButton>
                            </Tooltip>
                            {!closed && (
                              <Tooltip title="Close window">
                                <IconButton
                                  size="small"
                                  onClick={() => close(window)}
                                  disabled={deactivateMutation.isPending}
                                  aria-label="Close window"
                                  color="error"
                                >
                                  <Ban className="w-4 h-4" />
                                </IconButton>
                              </Tooltip>
                            )}
                          </TableCell>
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              </TableContainer>
            )}
          </Card>
        )}

        {doctorId === null && !doctorsQuery.isLoading && doctors.length === 0 && (
          <Card className="p-6">
            <EmptyState
              icon={<CalendarClock className="h-6 w-6 text-ink-300" />}
              message="Create a doctor first — availability belongs to doctors."
            />
          </Card>
        )}
      </div>

      {modalOpen && (
        <Modal title={editing ? 'Edit Window' : 'New Availability Window'} onClose={() => setModalOpen(false)}>
          <form onSubmit={form.handleSubmit(submit)} className="flex flex-col gap-4">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <Select label="Day" {...form.register('dayOfWeek', { valueAsNumber: true })} error={form.formState.errors.dayOfWeek?.message}>
                <option value="">Pick a day...</option>
                {DAYS.map((d) => (
                  <option key={d.value} value={d.value}>{d.label}</option>
                ))}
              </Select>
              <Select label="Repeats" {...form.register('recurringValue')}>
                <option value="true">Every week</option>
                <option value="false">One-off</option>
              </Select>
              <Input label="Start time" type="time" {...form.register('startTime')} error={form.formState.errors.startTime?.message} />
              <Input label="End time" type="time" {...form.register('endTime')} error={form.formState.errors.endTime?.message} />
              <Input label="Effective from (optional)" type="date" {...form.register('effectiveFrom')} error={form.formState.errors.effectiveFrom?.message} />
              <Input label="Effective until (optional)" type="date" {...form.register('effectiveUntil')} error={form.formState.errors.effectiveUntil?.message} />
            </div>
            {modalError && <ErrorBox message={modalError.message} />}
            <div className="flex justify-end gap-3 pt-2">
              <Button type="button" variant="outline" onClick={() => setModalOpen(false)}>Cancel</Button>
              <Button type="submit" disabled={pending}>
                {pending ? <span className="flex items-center gap-2"><Spinner className="text-white" /> Saving...</span> : editing ? 'Save Changes' : 'Add Window'}
              </Button>
            </div>
          </form>
        </Modal>
      )}
    </AppShell>
  );
}
