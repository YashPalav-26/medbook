import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useAdminSpecialties, useCreateSpecialty, useUpdateSpecialty, useDeactivateSpecialty } from '../../hooks/useAdmin';
import { AppShell } from '../../components/layout/AppShell';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { Input } from '../../components/ui/Input';
import { Modal } from '../../components/ui/Modal';
import { PageHeader, ActiveBadge, EmptyState, ErrorBox } from '../../components/admin/AdminUi';
import { toApiError } from '../../api/client';
import { Stethoscope, PlusCircle, Loader2, Ban, Pencil } from 'lucide-react';
import type { SpecialtyResponse } from '../../types/appointment';

const specialtySchema = z.object({
  name: z.string().min(1, 'Name is required').max(100, 'Name must be at most 100 characters'),
  description: z.string().max(5000, 'Description must be at most 5000 characters').optional(),
});
type SpecialtyFormData = z.infer<typeof specialtySchema>;

/**
 * ADMIN specialty management.
 * Lists every specialty (active and inactive) from the real admin API and supports
 * create / edit / deactivate through modals. Mutations invalidate the React Query
 * caches, so the list (and the booking page's discovery dropdown) refresh without
 * any full-page reload.
 */
export function SpecialtiesAdminPage() {
  const specialtiesQuery = useAdminSpecialties();
  const createMutation = useCreateSpecialty();
  const updateMutation = useUpdateSpecialty();
  const deactivateMutation = useDeactivateSpecialty();

  const [editing, setEditing] = useState<SpecialtyResponse | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [bannerError, setBannerError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<SpecialtyFormData>({
    resolver: zodResolver(specialtySchema),
    defaultValues: { name: '', description: '' },
  });

  const openCreate = () => {
    setEditing(null);
    reset({ name: '', description: '' });
    setBannerError(null);
    setModalOpen(true);
  };

  const openEdit = (specialty: SpecialtyResponse) => {
    setEditing(specialty);
    reset({ name: specialty.name, description: specialty.description ?? '' });
    setBannerError(null);
    setModalOpen(true);
  };

  const submit = async (data: SpecialtyFormData) => {
    setBannerError(null);
    try {
      if (editing) {
        await updateMutation.mutateAsync({ id: editing.id, payload: data });
      } else {
        await createMutation.mutateAsync(data);
      }
      setModalOpen(false);
    } catch {
      // Shown inside the modal via the mutation error below.
    }
  };

  const modalError = createMutation.error
    ? toApiError(createMutation.error)
    : updateMutation.error
      ? toApiError(updateMutation.error)
      : null;

  const deactivate = async (specialty: SpecialtyResponse) => {
    setBannerError(null);
    try {
      await deactivateMutation.mutateAsync(specialty.id);
    } catch (error) {
      setBannerError(toApiError(error).message);
    }
  };

  const specialties = specialtiesQuery.data ?? [];

  return (
    <AppShell>
      <div>
        <PageHeader
          icon={<Stethoscope className="w-8 h-8 text-blue-600" />}
          title="Manage Specialties"
          description="Create, rename or retire the medical specialties offered for booking."
        />

        <div className="flex justify-end mb-4">
          <Button onClick={openCreate} className="flex items-center gap-2">
            <PlusCircle className="w-4 h-4" />
            New Specialty
          </Button>
        </div>

        {bannerError && <div className="mb-4"><ErrorBox message={bannerError} /></div>}

        <Card className="p-6">
          {specialtiesQuery.isLoading ? (
            <EmptyState loading message="Loading specialties..." />
          ) : specialtiesQuery.isError ? (
            <ErrorBox message={toApiError(specialtiesQuery.error).message} />
          ) : specialties.length === 0 ? (
            <EmptyState
              icon={<Stethoscope className="w-8 h-8 text-gray-400" />}
              message="No specialties yet. Create the first one so doctors can be assigned."
            />
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-left text-gray-500 border-b border-gray-200">
                    <th className="py-2 pr-4 font-medium">Name</th>
                    <th className="py-2 pr-4 font-medium">Description</th>
                    <th className="py-2 pr-4 font-medium">Status</th>
                    <th className="py-2 font-medium text-right">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {specialties.map((specialty) => (
                    <tr key={specialty.id} className="border-b border-gray-100 last:border-0">
                      <td className="py-3 pr-4 font-medium text-gray-900">{specialty.name}</td>
                      <td className="py-3 pr-4 text-gray-600">{specialty.description ?? '—'}</td>
                      <td className="py-3 pr-4"><ActiveBadge active={specialty.active} /></td>
                      <td className="py-3 text-right whitespace-nowrap">
                        <Button variant="outline" className="mr-2 !py-1.5 !px-3 text-sm" onClick={() => openEdit(specialty)} aria-label={`Edit ${specialty.name}`}>
                          <Pencil className="w-4 h-4" />
                        </Button>
                        {specialty.active && (
                          <Button
                            variant="outline"
                            className="!py-1.5 !px-3 text-sm text-red-600 hover:bg-red-50"
                            onClick={() => deactivate(specialty)}
                            disabled={deactivateMutation.isPending}
                            aria-label={`Deactivate ${specialty.name}`}
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

      {modalOpen && (
        <Modal
          title={editing ? `Edit ${editing.name}` : 'New Specialty'}
          onClose={() => setModalOpen(false)}
        >
          <form onSubmit={handleSubmit(submit)} className="space-y-4">
            <Input label="Name" type="text" {...register('name')} error={errors.name?.message} placeholder="e.g. Cardiology" />
            <Input
              label="Description (optional)"
              type="text"
              {...register('description')}
              error={errors.description?.message}
              placeholder="What this specialty covers"
            />
            {modalError && <ErrorBox message={modalError.message} />}
            <div className="flex justify-end gap-3 pt-2">
              <Button type="button" variant="outline" onClick={() => setModalOpen(false)}>Cancel</Button>
              <Button type="submit" disabled={createMutation.isPending || updateMutation.isPending}>
                {createMutation.isPending || updateMutation.isPending ? (
                  <span className="flex items-center gap-2"><Loader2 className="w-4 h-4 animate-spin" /> Saving...</span>
                ) : editing ? 'Save Changes' : 'Create Specialty'}
              </Button>
            </div>
          </form>
        </Modal>
      )}
      </div>
    </AppShell>
  );
}
