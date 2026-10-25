import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useAdminDoctors, useAdminSpecialties, useCreateDoctor, useUpdateDoctor, useDeactivateDoctor } from '../../hooks/useAdmin';
import type { DoctorAdminResponse } from '../../api/adminApi';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { Input } from '../../components/ui/Input';
import { Select } from '../../components/ui/Select';
import { Modal } from '../../components/ui/Modal';
import { AppShell } from '../../components/layout/AppShell';
import { PageHeader, ActiveBadge, EmptyState, ErrorBox, Spinner } from '../../components/admin/AdminUi';
import { toApiError } from '../../api/client';
import { formatCurrency } from '../../lib/format';
import { UserCog, PlusCircle, Ban, Pencil } from 'lucide-react';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Paper from '@mui/material/Paper';
import IconButton from '@mui/material/IconButton';
import Tooltip from '@mui/material/Tooltip';





const doctorBaseSchema = {
  specialtyId: z.number({ error: 'Select a specialty' }).min(1, 'Select a specialty'),
  medicalLicenseNumber: z.string().min(1, 'License number is required').max(50, 'At most 50 characters'),
  yearsExperience: z.number({ error: 'Years of experience is required' })
    .int('Whole years only').min(0, 'Must be 0 or more').max(80, 'Must be at most 80'),
  bio: z.string().max(5000, 'At most 5000 characters').optional(),
  consultationFee: z.number({ error: 'Consultation fee is required' })
    .min(0, 'Must be zero or more'),
  profilePictureUrl: z.string().max(500, 'At most 500 characters').optional(),
};

const createSchema = z.object({
  ...doctorBaseSchema,
  email: z.string().min(1, 'Email is required').email('Must be a valid email'),
  password: z.string().min(8, 'At least 8 characters').max(72, 'At most 72 characters'),
  firstName: z.string().min(1, 'First name is required').max(100),
  lastName: z.string().min(1, 'Last name is required').max(100),
  phoneNumber: z.string().min(1, 'Phone number is required')
    .regex(/^\+?[0-9\s\-]{1,20}$/, 'Use digits, spaces, dashes, optional +'),
});
const editSchema = z.object(doctorBaseSchema);

type CreateFormData = z.infer<typeof createSchema>;
type EditFormData = z.infer<typeof editSchema>;








export function DoctorsAdminPage() {
  const doctorsQuery = useAdminDoctors();
  const specialtiesQuery = useAdminSpecialties();
  const createMutation = useCreateDoctor();
  const updateMutation = useUpdateDoctor();
  const deactivateMutation = useDeactivateDoctor();

  const [editing, setEditing] = useState<DoctorAdminResponse | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [bannerError, setBannerError] = useState<string | null>(null);

  const createForm = useForm<CreateFormData>({
    resolver: zodResolver(createSchema),
    defaultValues: {
      email: '', password: '', firstName: '', lastName: '', phoneNumber: '',
      specialtyId: undefined, medicalLicenseNumber: '', yearsExperience: undefined,
      bio: '', consultationFee: undefined, profilePictureUrl: '',
    },
  });
  const editForm = useForm<EditFormData>({
    resolver: zodResolver(editSchema),
    defaultValues: {
      specialtyId: undefined, medicalLicenseNumber: '', yearsExperience: undefined,
      bio: '', consultationFee: undefined, profilePictureUrl: '',
    },
  });

  const openCreate = () => {
    setEditing(null);
    createForm.reset();
    setBannerError(null);
    setModalOpen(true);
  };

  const openEdit = (doctor: DoctorAdminResponse) => {
    setEditing(doctor);
    editForm.reset({
      specialtyId: doctor.specialtyId,
      medicalLicenseNumber: doctor.medicalLicenseNumber,
      yearsExperience: doctor.yearsExperience,
      bio: doctor.bio ?? '',
      consultationFee: doctor.consultationFee,
      profilePictureUrl: doctor.profilePictureUrl ?? '',
    });
    setBannerError(null);
    setModalOpen(true);
  };

  const submitCreate = async (data: CreateFormData) => {
    setBannerError(null);
    try {
      await createMutation.mutateAsync(data);
      setModalOpen(false);
    } catch { /* shown in modal */ }
  };

  const submitEdit = async (data: EditFormData) => {
    setBannerError(null);
    if (!editing) return;
    try {
      await updateMutation.mutateAsync({ id: editing.id, payload: data });
      setModalOpen(false);
    } catch { /* shown in modal */ }
  };

  const modalError = createMutation.error
    ? toApiError(createMutation.error)
    : updateMutation.error
      ? toApiError(updateMutation.error)
      : null;

  const deactivate = async (doctor: DoctorAdminResponse) => {
    setBannerError(null);
    try {
      await deactivateMutation.mutateAsync(doctor.id);
    } catch (error) {
      setBannerError(toApiError(error).message);
    }
  };

  const doctors = doctorsQuery.data ?? [];
  const specialties = specialtiesQuery.data ?? [];
  const pending = createMutation.isPending || updateMutation.isPending;

  return (
    <AppShell>
      <div className="mx-auto max-w-5xl">
        <PageHeader
          icon={<UserCog className="h-6 w-6 text-brand-600" />}
          title="Manage Doctors"
          description="Doctor profiles with their login accounts. Creating a doctor provisions a DOCTOR account automatically."
        />

        <div className="flex justify-end mb-4">
          <Button onClick={openCreate} className="flex items-center gap-2">
            <PlusCircle className="w-4 h-4" />
            New Doctor
          </Button>
        </div>

        {bannerError && <div className="mb-4"><ErrorBox message={bannerError} /></div>}

        <Card className="p-6">
          {doctorsQuery.isLoading ? (
            <EmptyState loading message="Loading doctors..." />
          ) : doctorsQuery.isError ? (
            <ErrorBox message={toApiError(doctorsQuery.error).message} />
          ) : doctors.length === 0 ? (
            <EmptyState
              icon={<UserCog className="h-6 w-6 text-ink-300" />}
              message="No doctors yet. Create the first one — they will appear in booking discovery immediately."
            />
          ) : (
            <TableContainer component={Paper} className="overflow-x-auto !bg-transparent !shadow-none">
              <Table size="small" aria-label="Doctors">
                <TableHead>
                  <TableRow>
                    <TableCell>Doctor</TableCell>
                    <TableCell>Specialty</TableCell>
                    <TableCell className="hidden md:table-cell">License</TableCell>
                    <TableCell>Fee</TableCell>
                    <TableCell className="hidden md:table-cell">Experience</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell align="right">Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {doctors.map((doctor) => (
                    <TableRow key={doctor.id} hover>
                      <TableCell>
                        <div className="font-medium text-ink-900">Dr. {doctor.firstName} {doctor.lastName}</div>
                        <div className="text-ink-500">{doctor.email}</div>
                      </TableCell>
                      <TableCell className="text-ink-700">{doctor.specialty}</TableCell>
                      <TableCell className="hidden text-ink-700 md:table-cell">{doctor.medicalLicenseNumber}</TableCell>
                      <TableCell className="text-ink-700">{formatCurrency(doctor.consultationFee)}</TableCell>
                      <TableCell className="hidden text-ink-700 md:table-cell">{doctor.yearsExperience} yrs</TableCell>
                      <TableCell><ActiveBadge active={doctor.active && doctor.userActive} /></TableCell>
                      <TableCell align="right" className="whitespace-nowrap">
                        <Tooltip title={`Edit Dr. ${doctor.lastName}`}>
                          <IconButton size="small" onClick={() => openEdit(doctor)} aria-label={`Edit Dr. ${doctor.lastName}`}>
                            <Pencil className="w-4 h-4" />
                          </IconButton>
                        </Tooltip>
                        {doctor.active && (
                          <Tooltip title={`Deactivate Dr. ${doctor.lastName}`}>
                            <IconButton
                              size="small"
                              onClick={() => deactivate(doctor)}
                              disabled={deactivateMutation.isPending}
                              aria-label={`Deactivate Dr. ${doctor.lastName}`}
                              color="error"
                            >
                              <Ban className="w-4 h-4" />
                            </IconButton>
                          </Tooltip>
                        )}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </Card>
      </div>

      {modalOpen && !editing && (
        <Modal title="New Doctor" onClose={() => setModalOpen(false)}>
          <form onSubmit={createForm.handleSubmit(submitCreate)} className="flex flex-col gap-4">
            <p className="text-sm text-ink-600 bg-info-100/60 border border-info-100 rounded-field p-3">
              Creating a doctor also creates their <strong>login account</strong> with the
              DOCTOR role. They will sign in with the email and password you set here.
            </p>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <Input label="Email (login)" type="email" {...createForm.register('email')} error={createForm.formState.errors.email?.message} />
              <Input label="Password (min 8 chars)" type="password" {...createForm.register('password')} error={createForm.formState.errors.password?.message} />
              <Input label="First name" type="text" {...createForm.register('firstName')} error={createForm.formState.errors.firstName?.message} />
              <Input label="Last name" type="text" {...createForm.register('lastName')} error={createForm.formState.errors.lastName?.message} />
              <Input label="Phone number" type="text" {...createForm.register('phoneNumber')} error={createForm.formState.errors.phoneNumber?.message} placeholder="+1 555 000 0000" />
              <Select label="Specialty" {...createForm.register('specialtyId', { valueAsNumber: true })} error={createForm.formState.errors.specialtyId?.message}>
                <option value="">Select a specialty...</option>
                {specialties.filter((s) => s.active).map((s) => (
                  <option key={s.id} value={s.id}>{s.name}</option>
                ))}
              </Select>
              <Input label="Medical license number" type="text" {...createForm.register('medicalLicenseNumber')} error={createForm.formState.errors.medicalLicenseNumber?.message} />
              <Input label="Years of experience" type="number" {...createForm.register('yearsExperience', { valueAsNumber: true })} error={createForm.formState.errors.yearsExperience?.message} />
              <Input label="Consultation fee" type="number" step="0.01" {...createForm.register('consultationFee', { valueAsNumber: true })} error={createForm.formState.errors.consultationFee?.message} />
              <Input label="Profile picture URL (optional)" type="text" {...createForm.register('profilePictureUrl')} error={createForm.formState.errors.profilePictureUrl?.message} />
            </div>
            <Input label="Bio (optional)" type="text" {...createForm.register('bio')} error={createForm.formState.errors.bio?.message} />
            {modalError && <ErrorBox message={modalError.message} />}
            <div className="flex justify-end gap-3 pt-2">
              <Button type="button" variant="outline" onClick={() => setModalOpen(false)}>Cancel</Button>
              <Button type="submit" disabled={pending}>
                {pending ? <span className="flex items-center gap-2"><Spinner className="text-white" /> Creating...</span> : 'Create Doctor'}
              </Button>
            </div>
          </form>
        </Modal>
      )}

      {modalOpen && editing && (
        <Modal title={`Edit Dr. ${editing.lastName}`} onClose={() => setModalOpen(false)}>
          <form onSubmit={editForm.handleSubmit(submitEdit)} className="flex flex-col gap-4">
            <p className="text-sm text-ink-600">
              Editing the profile of <strong>Dr. {editing.firstName} {editing.lastName}</strong>
              {' '}({editing.email}). Login credentials are managed separately, not here.
            </p>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <Select label="Specialty" {...editForm.register('specialtyId', { valueAsNumber: true })} error={editForm.formState.errors.specialtyId?.message}>
                <option value="">Select a specialty...</option>
                {specialties.filter((s) => s.active).map((s) => (
                  <option key={s.id} value={s.id}>{s.name}</option>
                ))}
              </Select>
              <Input label="Medical license number" type="text" {...editForm.register('medicalLicenseNumber')} error={editForm.formState.errors.medicalLicenseNumber?.message} />
              <Input label="Years of experience" type="number" {...editForm.register('yearsExperience', { valueAsNumber: true })} error={editForm.formState.errors.yearsExperience?.message} />
              <Input label="Consultation fee" type="number" step="0.01" {...editForm.register('consultationFee', { valueAsNumber: true })} error={editForm.formState.errors.consultationFee?.message} />
              <Input label="Profile picture URL (optional)" type="text" {...editForm.register('profilePictureUrl')} error={editForm.formState.errors.profilePictureUrl?.message} />
            </div>
            <Input label="Bio (optional)" type="text" {...editForm.register('bio')} error={editForm.formState.errors.bio?.message} />
            {modalError && <ErrorBox message={modalError.message} />}
            <div className="flex justify-end gap-3 pt-2">
              <Button type="button" variant="outline" onClick={() => setModalOpen(false)}>Cancel</Button>
              <Button type="submit" disabled={pending}>
                {pending ? <span className="flex items-center gap-2"><Spinner className="text-white" /> Saving...</span> : 'Save Changes'}
              </Button>
            </div>
          </form>
        </Modal>
      )}
    </AppShell>
  );
}
