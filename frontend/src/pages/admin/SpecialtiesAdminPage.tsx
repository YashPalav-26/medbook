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
import { PageHeader, ActiveBadge, EmptyState, ErrorBox, Spinner } from '../../components/admin/AdminUi';
import { toApiError } from '../../api/client';
import { Stethoscope, PlusCircle, Ban, Pencil } from 'lucide-react';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Paper from '@mui/material/Paper';
import IconButton from '@mui/material/IconButton';
import Tooltip from '@mui/material/Tooltip';
import type { SpecialtyResponse } from '../../types/appointment';

const specialtySchema = z.object({
  name: z.string().min(1, 'Name is required').max(100, 'Name must be at most 100 characters'),
  description: z.string().max(5000, 'Description must be at most 5000 characters').optional(),
});
type SpecialtyFormData = z.infer<typeof specialtySchema>;








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
          icon={<Stethoscope className="h-6 w-6 text-brand-600" />}
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
              icon={<Stethoscope className="h-6 w-6 text-ink-300" />}
              message="No specialties yet. Create the first one so doctors can be assigned."
            />
          ) : (
            <TableContainer component={Paper} className="overflow-x-auto !bg-transparent !shadow-none">
              <Table size="small" aria-label="Specialties">
                <TableHead>
                  <TableRow>
                    <TableCell>Name</TableCell>
                    <TableCell>Description</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell align="right">Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {specialties.map((specialty) => (
                    <TableRow key={specialty.id} hover>
                      <TableCell className="!font-medium text-ink-900">{specialty.name}</TableCell>
                      <TableCell className="text-ink-600">{specialty.description ?? '—'}</TableCell>
                      <TableCell><ActiveBadge active={specialty.active} /></TableCell>
                      <TableCell align="right" className="whitespace-nowrap">
                        <Tooltip title={`Edit ${specialty.name}`}>
                          <IconButton size="small" onClick={() => openEdit(specialty)} aria-label={`Edit ${specialty.name}`}>
                            <Pencil className="w-4 h-4" />
                          </IconButton>
                        </Tooltip>
                        {specialty.active && (
                          <Tooltip title={`Deactivate ${specialty.name}`}>
                            <IconButton
                              size="small"
                              onClick={() => deactivate(specialty)}
                              disabled={deactivateMutation.isPending}
                              aria-label={`Deactivate ${specialty.name}`}
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

      {modalOpen && (
        <Modal
          title={editing ? `Edit ${editing.name}` : 'New Specialty'}
          onClose={() => setModalOpen(false)}
        >
          <form onSubmit={handleSubmit(submit)} className="flex flex-col gap-4">
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
                  <span className="flex items-center gap-2"><Spinner className="text-white" /> Saving...</span>
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
