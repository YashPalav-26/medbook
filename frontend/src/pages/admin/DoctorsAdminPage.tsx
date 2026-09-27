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
import { PageHeader, ActiveBadge, EmptyState, ErrorBox } from '../../components/admin/AdminUi';
import { toApiError } from '../../api/client';
import { UserCog, PlusCircle, Loader2, Ban, Pencil } from 'lucide-react';

/**
 * Shared Zod schema for the doctor form. Creation requires the password field;
 * edits never touch credentials (the backend rejects them there too).
 */
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

/**
 * ADMIN doctor management.
 * Lists all doctors with account + profile state, creates doctors (which
 * provisions the login account with the DOCTOR role server-side), edits profiles
 * and deactivates. Deactivation keeps history: it only removes the doctor from
 * booking discovery.
 */
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
          icon={<UserCog className="w-8 h-8 text-blue-600" />}
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
              icon={<UserCog className="w-8 h-8 text-gray-400" />}
              message="No doctors yet. Create the first one — they will appear in booking discovery immediately."
            />
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-left text-gray-500 border-b border-gray-200">
                    <th className="py-2 pr-4 font-medium">Doctor</th>
                    <th className="py-2 pr-4 font-medium">Specialty</th>
                    <th className="py-2 pr-4 font-medium">License</th>
                    <th className="py-2 pr-4 font-medium">Fee</th>
                    <th className="py-2 pr-4 font-medium">Experience</th>
                    <th className="py-2 pr-4 font-medium">Status</th>
                    <th className="py-2 font-medium text-right">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {doctors.map((doctor) => (
                    <tr key={doctor.id} className="border-b border-gray-100 last:border-0">
                      <td className="py-3 pr-4">
                        <div className="font-medium text-gray-900">Dr. {doctor.firstName} {doctor.lastName}</div>
                        <div className="text-gray-500">{doctor.email}</div>
                      </td>
                      <td className="py-3 pr-4 text-gray-700">{doctor.specialty}</td>
                      <td className="py-3 pr-4 text-gray-700">{doctor.medicalLicenseNumber}</td>
                      <td className="py-3 pr-4 text-gray-700">${doctor.consultationFee}</td>
                      <td className="py-3 pr-4 text-gray-700">{doctor.yearsExperience} yrs</td>
                      <td className="py-3 pr-4">
                        <ActiveBadge active={doctor.active && doctor.userActive} />
                      </td>
                      <td className="py-3 text-right whitespace-nowrap">
                        <Button variant="outline" className="mr-2 !py-1.5 !px-3 text-sm" onClick={() => openEdit(doctor)} aria-label={`Edit Dr. ${doctor.lastName}`}>
                          <Pencil className="w-4 h-4" />
                        </Button>
                        {doctor.active && (
                          <Button
                            variant="outline"
                            className="!py-1.5 !px-3 text-sm text-red-600 hover:bg-red-50"
                            onClick={() => deactivate(doctor)}
                            disabled={deactivateMutation.isPending}
                            aria-label={`Deactivate Dr. ${doctor.lastName}`}
                          >
                            <Ban className="w-4 h-4" />
                          </Button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>
      </div>

      {modalOpen && !editing && (
        <Modal title="New Doctor" onClose={() => setModalOpen(false)}>
          <form onSubmit={createForm.handleSubmit(submitCreate)} className="space-y-4">
            <p className="text-sm text-gray-600 bg-blue-50 border border-blue-100 rounded-lg p-3">
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
                {pending ? <span className="flex items-center gap-2"><Loader2 className="w-4 h-4 animate-spin" /> Creating...</span> : 'Create Doctor'}
              </Button>
            </div>
          </form>
        </Modal>
      )}

      {modalOpen && editing && (
        <Modal title={`Edit Dr. ${editing.lastName}`} onClose={() => setModalOpen(false)}>
          <form onSubmit={editForm.handleSubmit(submitEdit)} className="space-y-4">
            <p className="text-sm text-gray-600">
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
                {pending ? <span className="flex items-center gap-2"><Loader2 className="w-4 h-4 animate-spin" /> Saving...</span> : 'Save Changes'}
              </Button>
            </div>
          </form>
        </Modal>
      )}
    </AppShell>
  );
}
