import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import * as adminApi from '../api/adminApi';
import type {
  SpecialtyCreateRequest,
  SpecialtyUpdateRequest,
  DoctorCreateRequest,
  DoctorUpdateRequest,
  AvailabilityCreateRequest,
  AvailabilityUpdateRequest,
} from '../api/adminApi';
import type { AdminAppointmentFilters } from '../types/appointment';
import { useAuth } from './useAuth';

/**
 * React Query hooks for the admin area.
 *
 * Query keys: ['admin', resource, ...] — mutations invalidate every affected
 * resource so lists, detail views and the booking discovery API cache stay
 * consistent without full page reloads.
 */

// ------------------------------------------------------------------
// Specialties
// ------------------------------------------------------------------

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
      // Admin list + the public discovery dropdown both change.
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
      queryClient.invalidateQueries({ queryKey: ['doctors'] }); // doctor cards show specialty names
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

// ------------------------------------------------------------------
// Doctors
// ------------------------------------------------------------------

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
      queryClient.invalidateQueries({ queryKey: ['doctors'] }); // public discovery
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

// ------------------------------------------------------------------
// Availability
// ------------------------------------------------------------------

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
      queryClient.invalidateQueries({ queryKey: ['doctorAvailability'] }); // booking page
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

// ------------------------------------------------------------------
// Appointments
// ------------------------------------------------------------------

/**
 * Clinic-wide appointment list. The filters object is part of the query key, so
 * every filter combination gets its own cache entry and changing a filter refetches
 * from the backend (server-side filtering, not JavaScript filtering).
 */
export function useAdminAppointments(filters: AdminAppointmentFilters = {}) {
  const { isAuthenticated } = useAuth();

  return useQuery({
    queryKey: ['admin', 'appointments', filters],
    queryFn: () => adminApi.listAdminAppointments(filters),
    enabled: isAuthenticated,
  });
}

/** One appointment with its status-change audit trail. */
export function useAdminAppointment(id: number | null) {
  const { isAuthenticated } = useAuth();

  return useQuery({
    queryKey: ['admin', 'appointments', 'detail', id],
    queryFn: () => adminApi.getAdminAppointment(id!),
    enabled: isAuthenticated && id !== null,
  });
}

/** Real clinic overview metrics for the admin dashboard. */
export function useAdminOverview() {
  const { isAuthenticated } = useAuth();

  return useQuery({
    queryKey: ['admin', 'appointments', 'overview'],
    queryFn: adminApi.getAdminOverview,
    enabled: isAuthenticated,
  });
}

/**
 * ADMIN support cancellation. Success invalidates every admin appointment view —
 * the filtered lists (all of them, since any may include this row), the detail,
 * and the dashboard overview counts.
 */
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
