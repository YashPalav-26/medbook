import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMyProfile, useUpdateAccount, useUpdatePatientProfile, useUpdateDoctorProfile, useChangePassword } from '../hooks/useProfile';
import { toApiError } from '../api/client';
import { AppShell } from '../components/layout/AppShell';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { Input } from '../components/ui/Input';
import { Select } from '../components/ui/Select';

import { ErrorBox, Skeleton, EmptyState } from '../components/ui/Feedback';
import { toast } from '../lib/toast';
import { formatDate } from '../lib/dates';
import { formatCurrency } from '../lib/format';
import IconButton from '@mui/material/IconButton';
import InputAdornment from '@mui/material/InputAdornment';
import {
  UserRound, Lock, Pencil, CheckCircle2, X, Eye, EyeOff, Save, Phone, Mail,
  Stethoscope, HeartPulse, ShieldCheck,
} from 'lucide-react';














const accountSchema = z.object({
  firstName: z.string().min(1, 'First name is required').max(100, 'At most 100 characters'),
  lastName: z.string().min(1, 'Last name is required').max(100, 'At most 100 characters'),
  phoneNumber: z
    .string()
    .min(1, 'Phone number is required')
    .max(20, 'At most 20 characters')
    .regex(/^\+?[0-9\s-]+$/, 'Digits, spaces, dashes and optional leading + only'),
});
type AccountFormData = z.infer<typeof accountSchema>;

const patientProfileSchema = z.object({
  addressLine1: z.string().max(255, 'At most 255 characters'),
  addressLine2: z.string().max(255, 'At most 255 characters'),
  city: z.string().max(100, 'At most 100 characters'),
  stateProvince: z.string().max(50, 'At most 50 characters'),
  postalCode: z.string().max(20, 'At most 20 characters'),
  country: z.string().max(50, 'At most 50 characters'),
  emergencyContactName: z.string().max(200, 'At most 200 characters'),
  emergencyContactPhone: z
    .string()
    .max(20, 'At most 20 characters')
    .regex(/^\+?[0-9\s-]*$/, 'Digits, spaces, dashes and optional leading + only'),
  hasInsurance: z.boolean(),
  insuranceProvider: z.string().max(100, 'At most 100 characters'),
  insurancePolicyNumber: z.string().max(50, 'At most 50 characters'),
  allergies: z.string().max(5000, 'At most 5000 characters'),
});
type PatientProfileFormData = z.infer<typeof patientProfileSchema>;

const doctorProfileSchema = z.object({
  bio: z.string().max(5000, 'At most 5000 characters'),
  profilePictureUrl: z.string().max(500, 'At most 500 characters'),
});
type DoctorProfileFormData = z.infer<typeof doctorProfileSchema>;

const passwordSchema = z
  .object({
    currentPassword: z.string().min(1, 'Current password is required'),
    newPassword: z.string().min(8, 'At least 8 characters').max(72, 'At most 72 characters'),
    confirmPassword: z.string(),
  })
  .refine((data) => data.newPassword === data.confirmPassword, {
    message: 'Passwords do not match',
    path: ['confirmPassword'],
  });
type PasswordFormData = z.infer<typeof passwordSchema>;






function InfoRow({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex justify-between gap-4 py-1.5">
      <dt className="text-sm text-ink-500">{label}</dt>
      <dd className="max-w-[60%] text-right text-sm font-medium text-ink-800">{value}</dd>
    </div>
  );
}


function PasswordInput({
  label,
  error,
  registration,
  autoComplete,
}: {
  label: string;
  error?: string;
  registration: {
    ref: (instance: HTMLInputElement | null) => void;
    name: string;
    onChange: (event: React.ChangeEvent<HTMLInputElement>) => void;
    onBlur: (event: React.FocusEvent<HTMLInputElement>) => void;
  };
  autoComplete?: string;
}) {
  const [visible, setVisible] = useState(false);
  return (
    <Input
      label={label}
      type={visible ? 'text' : 'password'}
      autoComplete={autoComplete}
      error={error}
      {...registration}
      endAdornment={
        <InputAdornment position="end">
          <IconButton
            size="small"
            edge="end"
            onClick={() => setVisible((v) => !v)}
            aria-label={visible ? 'Hide password' : 'Show password'}
          >
            {visible
              ? <EyeOff className="h-4 w-4" aria-hidden="true" />
              : <Eye className="h-4 w-4" aria-hidden="true" />}
          </IconButton>
        </InputAdornment>
      }
    />
  );
}


function SectionHeader({
  icon,
  title,
  editing,
  onToggle,
}: {
  icon: React.ReactNode;
  title: string;
  editing: boolean;
  onToggle: () => void;
}) {
  return (
    <div className="mb-4 flex items-center justify-between gap-3">
      <h2 className="flex items-center gap-2 font-display text-lg font-semibold text-ink-900">
        {icon}
        {title}
      </h2>
      <Button variant="outline" size="sm" onClick={onToggle}>
        {editing ? (
          <>
            <X className="h-3.5 w-3.5" aria-hidden="true" />
            Cancel Edit
          </>
        ) : (
          <>
            <Pencil className="h-3.5 w-3.5" aria-hidden="true" />
            Edit
          </>
        )}
      </Button>
    </div>
  );
}


function FormActions({ onCancel, saving, onCancelLabel = 'Cancel' }: { onCancel: () => void; saving: boolean; onCancelLabel?: string }) {
  return (
    <div className="flex gap-3 pt-2">
      <Button type="submit" disabled={saving}>
        <Save className="h-4 w-4" aria-hidden="true" />
        {saving ? 'Saving…' : 'Save Changes'}
      </Button>
      <Button type="button" variant="outline" onClick={onCancel} disabled={saving}>
        <X className="h-4 w-4" aria-hidden="true" />
        {onCancelLabel}
      </Button>
    </div>
  );
}





export function ProfilePage() {

  const { data: profile, isLoading, isError, error, refetch } = useMyProfile();

  const isPatient = Boolean(profile?.patient);
  const isDoctor = Boolean(profile?.doctor);
  const isAdminOnly = Boolean(profile && !isPatient && !isDoctor && profile.roles.includes('ROLE_ADMIN'));


  const [editingAccount, setEditingAccount] = useState(false);
  const accountForm = useForm<AccountFormData>({ resolver: zodResolver(accountSchema) });
  const updateAccount = useUpdateAccount();

  const startAccountEdit = () => {
    accountForm.reset({
      firstName: profile?.firstName ?? '',
      lastName: profile?.lastName ?? '',
      phoneNumber: profile?.phoneNumber ?? '',
    });
    setEditingAccount(true);
  };

  const onAccountSubmit = async (data: AccountFormData) => {
    try {
      await updateAccount.mutateAsync(data);
      setEditingAccount(false);
      toast.success('Account details updated.');
    } catch {
      // Surfaced through updateAccount.error below.
    }
  };
  const accountError = updateAccount.error ? toApiError(updateAccount.error) : null;


  const [editingPatient, setEditingPatient] = useState(false);
  const patientForm = useForm<PatientProfileFormData>({ resolver: zodResolver(patientProfileSchema) });
  const updatePatient = useUpdatePatientProfile();
  const watchHasInsurance = patientForm.watch('hasInsurance');

  const startPatientEdit = () => {

    patientForm.reset({
      addressLine1: profile?.patient?.addressLine1 ?? '',
      addressLine2: profile?.patient?.addressLine2 ?? '',
      city: profile?.patient?.city ?? '',
      stateProvince: profile?.patient?.stateProvince ?? '',
      postalCode: profile?.patient?.postalCode ?? '',
      country: profile?.patient?.country ?? '',
      emergencyContactName: profile?.patient?.emergencyContactName ?? '',
      emergencyContactPhone: profile?.patient?.emergencyContactPhone ?? '',
      hasInsurance: profile?.patient?.hasInsurance ?? false,
      insuranceProvider: profile?.patient?.insuranceProvider ?? '',
      insurancePolicyNumber: profile?.patient?.insurancePolicyNumber ?? '',
      allergies: profile?.patient?.allergies ?? '',
    });
    setEditingPatient(true);
  };

  const onPatientSubmit = async (data: PatientProfileFormData) => {
    try {
      await updatePatient.mutateAsync(data);
      setEditingPatient(false);
      toast.success('Patient profile updated.');
    } catch {
      // Surfaced through updatePatient.error below.
    }
  };
  const patientError = updatePatient.error ? toApiError(updatePatient.error) : null;


  const [editingDoctor, setEditingDoctor] = useState(false);
  const doctorForm = useForm<DoctorProfileFormData>({ resolver: zodResolver(doctorProfileSchema) });
  const updateDoctor = useUpdateDoctorProfile();

  const startDoctorEdit = () => {
    doctorForm.reset({
      bio: profile?.doctor?.bio ?? '',
      profilePictureUrl: profile?.doctor?.profilePictureUrl ?? '',
    });
    setEditingDoctor(true);
  };

  const onDoctorSubmit = async (data: DoctorProfileFormData) => {
    try {
      await updateDoctor.mutateAsync(data);
      setEditingDoctor(false);
      toast.success('Professional profile updated.');
    } catch {
      // Surfaced through updateDoctor.error below.
    }
  };
  const doctorError = updateDoctor.error ? toApiError(updateDoctor.error) : null;


  const passwordForm = useForm<PasswordFormData>({ resolver: zodResolver(passwordSchema) });
  const changePassword = useChangePassword();
  const [passwordChanged, setPasswordChanged] = useState(false);

  const onPasswordSubmit = async (data: PasswordFormData) => {
    try {
      await changePassword.mutateAsync({ currentPassword: data.currentPassword, newPassword: data.newPassword });
      passwordForm.reset();
      setPasswordChanged(true);
      toast.success('Password changed. Your current session stays signed in.');
    } catch {
      // Surfaced through changePassword.error below.
    }
  };
  const passwordError = changePassword.error ? toApiError(changePassword.error) : null;

  if (isLoading) {
    return (
      <AppShell title="My Profile">
        <div className="space-y-6">
          <Card className="space-y-3 p-6">
            <Skeleton className="h-5 w-40" />
            <Skeleton className="h-4 w-64" />
            <Skeleton className="h-4 w-52" />
          </Card>
          <Card className="space-y-3 p-6">
            <Skeleton className="h-5 w-40" />
            <Skeleton className="h-4 w-full" />
            <Skeleton className="h-4 w-2/3" />
          </Card>
        </div>
      </AppShell>
    );
  }

  if (isError) {
    const apiError = toApiError(error);
    return (
      <AppShell title="My Profile">
        <ErrorBox message={apiError.message} onRetry={() => refetch()} />
      </AppShell>
    );
  }

  if (!profile) {
    return (
      <AppShell title="My Profile">
        <Card className="p-6">
          <EmptyState message="Profile could not be loaded." />
        </Card>
      </AppShell>
    );
  }

  return (
    <AppShell title="My Profile">
      <div className="space-y-6">
        {/* ------------------------------------------------ Account */}
        <Card className="p-6">
          <SectionHeader
            icon={<UserRound className="h-5 w-5 text-brand-600" aria-hidden="true" />}
            title="Account"
            editing={editingAccount}
            onToggle={() => (editingAccount ? setEditingAccount(false) : startAccountEdit())}
          />
          {editingAccount ? (
            <form onSubmit={accountForm.handleSubmit(onAccountSubmit)} className="flex flex-col gap-4">
              <div className="grid gap-4 sm:grid-cols-2">
                <Input label="First name" {...accountForm.register('firstName')} error={accountForm.formState.errors.firstName?.message} />
                <Input label="Last name" {...accountForm.register('lastName')} error={accountForm.formState.errors.lastName?.message} />
              </div>
              <Input label="Phone number" type="tel" {...accountForm.register('phoneNumber')} error={accountForm.formState.errors.phoneNumber?.message} />
              <p className="text-xs text-ink-500">Email is your sign-in identity and cannot be changed here.</p>
              {accountError && <ErrorBox message={accountError.message} />}
              <FormActions onCancel={() => setEditingAccount(false)} saving={updateAccount.isPending} />
            </form>
          ) : (
            <dl>
              <InfoRow label="Name" value={`${profile.firstName} ${profile.lastName}`} />
              <InfoRow
                label="Email"
                value={
                  <span className="inline-flex items-center gap-1.5">
                    <Mail className="h-3.5 w-3.5 text-ink-400" aria-hidden="true" />
                    {profile.email}
                  </span>
                }
              />
              <InfoRow
                label="Phone"
                value={
                  <span className="inline-flex items-center gap-1.5">
                    <Phone className="h-3.5 w-3.5 text-ink-400" aria-hidden="true" />
                    {profile.phoneNumber || '—'}
                  </span>
                }
              />
              <InfoRow label="Roles" value={profile.roles.map((role) => role.replace('ROLE_', '')).join(', ')} />
            </dl>
          )}
        </Card>

        {/* ------------------------------------------------ Patient */}
        {isPatient && profile.patient && (
          <Card className="p-6">
            <SectionHeader
              icon={<HeartPulse className="h-5 w-5 text-danger-500" aria-hidden="true" />}
              title="Patient Profile"
              editing={editingPatient}
              onToggle={() => (editingPatient ? setEditingPatient(false) : startPatientEdit())}
            />
            {editingPatient ? (
              <form onSubmit={patientForm.handleSubmit(onPatientSubmit)} className="flex flex-col gap-4">
                <div className="grid gap-4 sm:grid-cols-2">
                  <Input label="Address line 1" {...patientForm.register('addressLine1')} error={patientForm.formState.errors.addressLine1?.message} />
                  <Input label="Address line 2" {...patientForm.register('addressLine2')} error={patientForm.formState.errors.addressLine2?.message} />
                  <Input label="City" {...patientForm.register('city')} error={patientForm.formState.errors.city?.message} />
                  <Input label="State / province" {...patientForm.register('stateProvince')} error={patientForm.formState.errors.stateProvince?.message} />
                  <Input label="Postal code" {...patientForm.register('postalCode')} error={patientForm.formState.errors.postalCode?.message} />
                  <Input label="Country" {...patientForm.register('country')} error={patientForm.formState.errors.country?.message} />
                </div>
                <div className="grid gap-4 sm:grid-cols-2">
                  <Input label="Emergency contact name" {...patientForm.register('emergencyContactName')} error={patientForm.formState.errors.emergencyContactName?.message} />
                  <Input label="Emergency contact phone" type="tel" {...patientForm.register('emergencyContactPhone')} error={patientForm.formState.errors.emergencyContactPhone?.message} />
                </div>
                <div className="grid gap-4 sm:grid-cols-2">
                  {/* Controlled: HTML selects only carry strings, so register() would put
                      "true"/"false" strings into a boolean field — setValue keeps it a real
                      boolean for Zod and the payload. */}
                  <Select
                    label="Insurance status"
                    value={watchHasInsurance ? 'true' : 'false'}
                    onChange={(e) => patientForm.setValue('hasInsurance', e.target.value === 'true')}
                  >
                    <option value="true">Has insurance</option>
                    <option value="false">No insurance</option>
                  </Select>
                  <Input label="Allergies" {...patientForm.register('allergies')} error={patientForm.formState.errors.allergies?.message} placeholder="e.g. Peanuts, latex" />
                </div>
                {watchHasInsurance && (
                  <div className="grid gap-4 sm:grid-cols-2">
                    <Input label="Insurance provider" {...patientForm.register('insuranceProvider')} error={patientForm.formState.errors.insuranceProvider?.message} />
                    <Input label="Policy number" {...patientForm.register('insurancePolicyNumber')} error={patientForm.formState.errors.insurancePolicyNumber?.message} />
                  </div>
                )}
                {patientError && <ErrorBox message={patientError.message} />}
                <FormActions onCancel={() => setEditingPatient(false)} saving={updatePatient.isPending} />
              </form>
            ) : (
              <dl>
                <InfoRow label="Date of birth" value={formatDate(profile.patient.dateOfBirth)} />
                <InfoRow
                  label="Address"
                  value={
                    [profile.patient.addressLine1, profile.patient.addressLine2, profile.patient.city, profile.patient.stateProvince, profile.patient.postalCode, profile.patient.country]
                      .filter(Boolean)
                      .join(', ') || '—'
                  }
                />
                <InfoRow
                  label="Emergency contact"
                  value={
                    profile.patient.emergencyContactName
                      ? `${profile.patient.emergencyContactName} · ${profile.patient.emergencyContactPhone ?? ''}`
                      : '—'
                  }
                />
                <InfoRow
                  label="Insurance"
                  value={
                    profile.patient.hasInsurance
                      ? [profile.patient.insuranceProvider, profile.patient.insurancePolicyNumber].filter(Boolean).join(' · ') || 'Has insurance'
                      : 'No insurance'
                  }
                />
                <InfoRow label="Allergies" value={profile.patient.allergies || '—'} />
              </dl>
            )}
          </Card>
        )}

        {/* ------------------------------------------------ Doctor */}
        {isDoctor && profile.doctor && (
          <Card className="p-6">
            <SectionHeader
              icon={<Stethoscope className="h-5 w-5 text-brand-600" aria-hidden="true" />}
              title="Professional Profile"
              editing={editingDoctor}
              onToggle={() => (editingDoctor ? setEditingDoctor(false) : startDoctorEdit())}
            />
            {editingDoctor ? (
              <form onSubmit={doctorForm.handleSubmit(onDoctorSubmit)} className="flex flex-col gap-4">
                <Input
                  label="Bio"
                  multiline
                  minRows={4}
                  placeholder="Tell patients about your practice"
                  {...doctorForm.register('bio')}
                  error={doctorForm.formState.errors.bio?.message}
                />
                <Input label="Profile picture URL" {...doctorForm.register('profilePictureUrl')} error={doctorForm.formState.errors.profilePictureUrl?.message} placeholder="https://…" />
                {doctorError && <ErrorBox message={doctorError.message} />}
                <FormActions onCancel={() => setEditingDoctor(false)} saving={updateDoctor.isPending} />
              </form>
            ) : (
              <dl>
                <InfoRow label="Bio" value={profile.doctor.bio || '—'} />
                <InfoRow label="Specialty (managed by clinic administration)" value={profile.doctor.specialtyName} />
                <InfoRow label="Medical license (managed by clinic administration)" value={profile.doctor.medicalLicenseNumber} />
                <InfoRow label="Consultation fee (managed by clinic administration)" value={formatCurrency(profile.doctor.consultationFee)} />
                <InfoRow label="Status (managed by clinic administration)" value={profile.doctor.active ? 'Active' : 'Inactive'} />
              </dl>
            )}
          </Card>
        )}

        {/* ------------------------------------------------ Admin-only note */}
        {isAdminOnly && (
          <Card className="p-6">
            <h2 className="flex items-center gap-2 font-display text-lg font-semibold text-ink-900">
              <ShieldCheck className="h-5 w-5 text-brand-600" aria-hidden="true" />
              Administrator
            </h2>
            <p className="mt-2 text-sm text-ink-500">
              Clinic administrators work from the admin area — there is no separate admin profile. Your account details above are shared across MedBook.
            </p>
          </Card>
        )}

        {/* ------------------------------------------------ Change password */}
        <Card className="p-6">
          <h2 className="mb-4 flex items-center gap-2 font-display text-lg font-semibold text-ink-900">
            <Lock className="h-5 w-5 text-ink-600" aria-hidden="true" />
            Change Password
          </h2>
          {passwordChanged && (
            <div className="mb-4 flex items-center gap-2 rounded-field border border-success-600/30 bg-success-100/60 p-3 text-sm text-success-700" role="status">
              <CheckCircle2 className="h-4 w-4" aria-hidden="true" />
              Password changed. Your current session stays signed in.
            </div>
          )}
          <form onSubmit={passwordForm.handleSubmit(onPasswordSubmit)} className="max-w-md flex flex-col gap-4">
            <PasswordInput
              label="Current password"
              registration={passwordForm.register('currentPassword')}
              error={passwordForm.formState.errors.currentPassword?.message}
              autoComplete="current-password"
            />
            <PasswordInput
              label="New password"
              registration={passwordForm.register('newPassword')}
              error={passwordForm.formState.errors.newPassword?.message}
              autoComplete="new-password"
            />
            <PasswordInput
              label="Confirm new password"
              registration={passwordForm.register('confirmPassword')}
              error={passwordForm.formState.errors.confirmPassword?.message}
              autoComplete="new-password"
            />
            <p className="text-xs text-ink-500">Use at least 8 characters. You stay signed in after changing your password.</p>
            {passwordError && <ErrorBox message={passwordError.message} />}
            <div className="flex gap-3">
              <Button type="submit" disabled={changePassword.isPending}>
                <Lock className="h-4 w-4" aria-hidden="true" />
                {changePassword.isPending ? 'Changing…' : 'Change Password'}
              </Button>
              {passwordChanged && (
                <Button type="button" variant="outline" onClick={() => { passwordForm.reset(); setPasswordChanged(false); }}>
                  <X className="h-4 w-4" aria-hidden="true" />
                  Dismiss
                </Button>
              )}
            </div>
          </form>
        </Card>
      </div>
    </AppShell>
  );
}
