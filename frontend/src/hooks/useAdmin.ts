import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import * as adminApi from '../api/adminApi';
import type {
  SpecialtyCreateRequest,
  SpecialtyUpdateRequest,
  DoctorCreateRequest,
  DoctorUpdateRequest,
  AvailabilityCreateRequest,
  AvailabilityUpdateRequest,
  AdminCreateRequest,
} from '../api/adminApi';
import type { AdminAppointmentFilters } from '../types/appointment';
import { useAuth } from './useAuth';

export function useAdminAccounts() {
  const { isAuthenticated } = useAuth();

  return useQuery({
    queryKey: ['admin', 'admins'],
    queryFn: adminApi.listAdminAccounts,
    enabled: isAuthenticated,
  });
}

export function useCreateAdminAccount() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: AdminCreateRequest) => adminApi.createAdminAccount(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'admins'] });
    },
  });
}

export function useAdminSpecialties() {
  const { isAuthenticated } = useAuth();

  return useQuery({
    queryKey: ['admin', 'specialties'],
    queryFn: adminApi.listAdminSpecialties,
    enabled: isAuthenticated,
  });
}

export function useCreateSpecialty() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: SpecialtyCreateRequest) => adminApi.createSpecialty(payload),
    onSuccess: () => {

      queryClient.invalidateQueries({ queryKey: ['admin', 'specialties'] });
      queryClient.invalidateQueries({ queryKey: ['specialties'] });
    },
  });
}

export function useUpdateSpecialty() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: SpecialtyUpdateRequest }) =>
      adminApi.updateSpecialty(id, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'specialties'] });
      queryClient.invalidateQueries({ queryKey: ['specialties'] });
      queryClient.invalidateQueries({ queryKey: ['doctors'] });
    },
  });
}

export function useDeactivateSpecialty() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: number) => adminApi.deactivateSpecialty(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'specialties'] });
      queryClient.invalidateQueries({ queryKey: ['specialties'] });
      queryClient.invalidateQueries({ queryKey: ['doctors'] });
    },
  });
}

export function useAdminDoctors() {
  const { isAuthenticated } = useAuth();

  return useQuery({
    queryKey: ['admin', 'doctors'],
    queryFn: adminApi.listAdminDoctors,
    enabled: isAuthenticated,
  });
}

export function useCreateDoctor() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: DoctorCreateRequest) => adminApi.createDoctor(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'doctors'] });
      queryClient.invalidateQueries({ queryKey: ['doctors'] });
    },
  });
}

export function useUpdateDoctor() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: DoctorUpdateRequest }) =>
      adminApi.updateDoctor(id, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'doctors'] });
      queryClient.invalidateQueries({ queryKey: ['doctors'] });
      queryClient.invalidateQueries({ queryKey: ['doctorAvailability'] });
    },
  });
}

export function useDeactivateDoctor() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: number) => adminApi.deactivateDoctor(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'doctors'] });
      queryClient.invalidateQueries({ queryKey: ['doctors'] });
      queryClient.invalidateQueries({ queryKey: ['doctorAvailability'] });
    },
  });
}

export function useAdminPatients() {
  const { isAuthenticated } = useAuth();

  return useQuery({
    queryKey: ['admin', 'patients'],
    queryFn: adminApi.listAdminPatients,
    enabled: isAuthenticated,
    staleTime: 5 * 60 * 1000,
  });
}

export function useAdminAvailability(doctorId: number | null) {
  const { isAuthenticated } = useAuth();

  return useQuery({
    queryKey: ['admin', 'availability', doctorId],
    queryFn: () => adminApi.listAdminAvailability(doctorId!),
    enabled: isAuthenticated && doctorId !== null,
  });
}

export function useCreateAvailability() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ doctorId, payload }: { doctorId: number; payload: AvailabilityCreateRequest }) =>
      adminApi.createAvailability(doctorId, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'availability'] });
      queryClient.invalidateQueries({ queryKey: ['doctorAvailability'] });
    },
  });
}

export function useUpdateAvailability() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: AvailabilityUpdateRequest }) =>
      adminApi.updateAvailability(id, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'availability'] });
      queryClient.invalidateQueries({ queryKey: ['doctorAvailability'] });
    },
  });
}

export function useDeactivateAvailability() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: number) => adminApi.deactivateAvailability(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'availability'] });
      queryClient.invalidateQueries({ queryKey: ['doctorAvailability'] });
    },
  });
}

export function useAdminAppointments(filters: AdminAppointmentFilters = {}) {
  const { isAuthenticated } = useAuth();

  return useQuery({
    queryKey: ['admin', 'appointments', filters],
    queryFn: () => adminApi.listAdminAppointments(filters),
    enabled: isAuthenticated,
  });
}

export function useAdminAppointment(id: number | null) {
  const { isAuthenticated } = useAuth();

  return useQuery({
    queryKey: ['admin', 'appointments', 'detail', id],
    queryFn: () => adminApi.getAdminAppointment(id!),
    enabled: isAuthenticated && id !== null,
  });
}

export function useAdminOverview() {
  const { isAuthenticated } = useAuth();

  return useQuery({
    queryKey: ['admin', 'appointments', 'overview'],
    queryFn: adminApi.getAdminOverview,
    enabled: isAuthenticated,
  });
}

export function useAdminCancelAppointment() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, reason }: { id: number; reason: string }) =>
      adminApi.cancelAppointmentAsAdmin(id, reason),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'appointments'] });
    },
  });
}
