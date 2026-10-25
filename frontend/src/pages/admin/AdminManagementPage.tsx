import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useAdminAccounts, useCreateAdminAccount } from '../../hooks/useAdmin';
import type { AdminAccountResponse } from '../../api/adminApi';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { Input } from '../../components/ui/Input';
import { Modal } from '../../components/ui/Modal';
import { AppShell } from '../../components/layout/AppShell';
import { PageHeader, ActiveBadge, EmptyState, ErrorBox, Spinner } from '../../components/admin/AdminUi';
import { toApiError } from '../../api/client';
import { ShieldCheck, PlusCircle } from 'lucide-react';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Paper from '@mui/material/Paper';

/**
 * Zod schema for the Add Admin form. Mirrors the backend's AdminCreateRequest
 * validation exactly (email, 8-72 char password, names, phone pattern) so the
 * admin gets the same feedback the API would give.
 */
const createSchema = z.object({
  email: z.string().min(1, 'Email is required').email('Must be a valid email'),
  password: z.string().min(8, 'At least 8 characters').max(72, 'At most 72 characters'),
  firstName: z.string().min(1, 'First name is required').max(100),
  lastName: z.string().min(1, 'Last name is required').max(100),
  phoneNumber: z.string().min(1, 'Phone number is required')
    .regex(/^\+?[0-9\s\-]{1,20}$/, 'Use digits, spaces, dashes, optional +'),
});

type CreateFormData = z.infer<typeof createSchema>;

/**
 * ADMIN account management (GET/POST /api/v1/admin/admins).
 *
 * Lists every administrator (name, email, account status, roles — never any
 * password) and creates additional administrators. Creating an admin provisions
 * their login account with the ADMIN role server-side; the frontend sends no
 * role field because the backend is authoritative. Deactivation with
 * last-admin lockout protection is a documented future feature, so this screen
 * offers list + add only.
 */
export function AdminManagementPage() {
  const adminsQuery = useAdminAccounts();
  const createMutation = useCreateAdminAccount();

  const [modalOpen, setModalOpen] = useState(false);
  const [bannerError, setBannerError] = useState<string | null>(null);

  const createForm = useForm<CreateFormData>({
    resolver: zodResolver(createSchema),
    defaultValues: { email: '', password: '', firstName: '', lastName: '', phoneNumber: '' },
  });

  const openCreate = () => {
    createForm.reset();
    setBannerError(null);
    setModalOpen(true);
  };

  const submitCreate = async (data: CreateFormData) => {
    setBannerError(null);
    try {
      await createMutation.mutateAsync(data);
      setModalOpen(false);
    } catch { /* shown inside the modal */ }
  };

  const modalError = createMutation.error ? toApiError(createMutation.error) : null;
  const admins = adminsQuery.data ?? [];

  return (
    <AppShell>
      <div className="mx-auto max-w-5xl">
        <PageHeader
          icon={<ShieldCheck className="h-6 w-6 text-brand-600" />}
          title="Admin Management"
          description="Administrator accounts with login access. Creating an admin provisions an ADMIN account automatically."
        />

        <div className="flex justify-end mb-4">
          <Button onClick={openCreate} className="flex items-center gap-2">
            <PlusCircle className="w-4 h-4" />
            Add Admin
          </Button>
        </div>

        {bannerError && <div className="mb-4"><ErrorBox message={bannerError} /></div>}

        <Card className="p-6">
          {adminsQuery.isLoading ? (
            <EmptyState loading message="Loading administrators..." />
          ) : adminsQuery.isError ? (
            <ErrorBox message={toApiError(adminsQuery.error).message} />
          ) : admins.length === 0 ? (
            <EmptyState
              icon={<ShieldCheck className="h-6 w-6 text-ink-300" />}
              message="No administrator accounts yet. Add the first one — they will be able to sign in immediately."
            />
          ) : (
            <TableContainer component={Paper} className="overflow-x-auto !bg-transparent !shadow-none">
              <Table size="small" aria-label="Administrators">
                <TableHead>
                  <TableRow>
                    <TableCell>Name</TableCell>
                    <TableCell>Email</TableCell>
                    <TableCell className="hidden sm:table-cell">Phone</TableCell>
                    <TableCell>Roles</TableCell>
                    <TableCell>Status</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {admins.map((admin: AdminAccountResponse) => (
                    <TableRow key={admin.id} hover>
                      <TableCell>
                        <div className="font-medium text-ink-900">{admin.firstName} {admin.lastName}</div>
                      </TableCell>
                      <TableCell className="text-ink-600">{admin.email}</TableCell>
                      <TableCell className="hidden text-ink-600 sm:table-cell">{admin.phoneNumber}</TableCell>
                      <TableCell className="text-ink-700">
                        {admin.roles.map((role) => role.replace('ROLE_', '')).join(', ')}
                      </TableCell>
                      <TableCell><ActiveBadge active={admin.active && !admin.locked} /></TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </Card>
      </div>

      {modalOpen && (
        <Modal title="Add Admin" onClose={() => setModalOpen(false)}>
          <form onSubmit={createForm.handleSubmit(submitCreate)} className="space-y-4">
            <p className="text-sm text-ink-600 bg-info-100/60 border border-info-100 rounded-field p-3">
              Creating an admin also creates their <strong>login account</strong> with the
              ADMIN role. They will sign in at the shared <strong>/login</strong> page with
              the email and password you set here.
            </p>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <Input label="Email (login)" type="email" {...createForm.register('email')} error={createForm.formState.errors.email?.message} />
              <Input label="Password (min 8 chars)" type="password" {...createForm.register('password')} error={createForm.formState.errors.password?.message} />
              <Input label="First name" type="text" {...createForm.register('firstName')} error={createForm.formState.errors.firstName?.message} />
              <Input label="Last name" type="text" {...createForm.register('lastName')} error={createForm.formState.errors.lastName?.message} />
              <Input label="Phone number" type="text" {...createForm.register('phoneNumber')} error={createForm.formState.errors.phoneNumber?.message} placeholder="+1 555 000 0000" />
            </div>
            {modalError && <ErrorBox message={modalError.message} />}
            <div className="flex justify-end gap-3 pt-2">
              <Button type="button" variant="outline" onClick={() => setModalOpen(false)}>Cancel</Button>
              <Button type="submit" disabled={createMutation.isPending}>
                {createMutation.isPending
                  ? <span className="flex items-center gap-2"><Spinner className="text-white" /> Creating...</span>
                  : 'Create Admin'}
              </Button>
            </div>
          </form>
        </Modal>
      )}
    </AppShell>
  );
}
