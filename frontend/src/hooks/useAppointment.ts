import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import * as appointmentApi from '../api/appointmentApi';
import type {
  CreateAppointmentRequest,
  AdminCreateAppointmentRequest,
  CancelAppointmentRequest,
} from '../types/appointment';
import { useAuth } from './useAuth';

export function useAppointment(id: number | null) {
  const { isAuthenticated } = useAuth();

  return useQuery({
    queryKey: ['appointment', id],
    queryFn: () => appointmentApi.getAppointment(id!),
    enabled: isAuthenticated && id !== null,
    retry: 1,
  });
}

export function useCreateAppointment() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: CreateAppointmentRequest) => appointmentApi.createAppointment(data),
    onSuccess: (response) => {
      queryClient.invalidateQueries({ queryKey: ['appointments'] });
      queryClient.setQueryData(['appointment', response.id], response);
    },
  });
}

/**
 * ADMIN support booking (POST /appointments/admin — book on behalf of a patient).
 * Invalidates the admin appointment lists and the dashboard overview so the new
 * booking appears everywhere the admin looks.
 */
export function useCreateAppointmentForPatient() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: AdminCreateAppointmentRequest) =>
      appointmentApi.createAppointmentForPatient(data),
    onSuccess: (response) => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'appointments'] });
      queryClient.setQueryData(['appointment', response.id], response);
    },
  });
}

export function useCancelAppointment() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, data }: { id: number; data: CancelAppointmentRequest }) =>
      appointmentApi.cancelAppointment(id, data),
    onSuccess: (response) => {
      queryClient.invalidateQueries({ queryKey: ['appointments'] });
      queryClient.invalidateQueries({ queryKey: ['appointment', response.id] });
      queryClient.setQueryData(['appointment', response.id], response);
    },
  });
}

/**
 * Active specialties for the booking dropdown.
 * Static reference data: never stale within a session, so no refetch churn.
 */
export function useSpecialties() {
  const { isAuthenticated } = useAuth();

  return useQuery({
    queryKey: ['specialties'],
    queryFn: appointmentApi.getSpecialties,
    enabled: isAuthenticated,
    // Refetch at most once a minute. (staleTime: Infinity here previously kept an
    // empty list cached forever if the page was opened before any specialty existed.)
    staleTime: 60 * 1000,
  });
}

/**
 * Active doctors, optionally narrowed to one specialty.
 * The specialtyId is part of the query key, so switching specialties in the
 * booking form fetches exactly the doctors of that specialty.
 */
export function useDoctors(specialtyId: number | null) {
  const { isAuthenticated } = useAuth();

  // An empty <option> registers as NaN with valueAsNumber: treat any non-finite
  // id as "no specialty" instead of firing a request keyed on NaN.
  const validSpecialtyId = specialtyId !== null && Number.isFinite(specialtyId) ? specialtyId : null;

  return useQuery({
    queryKey: ['doctors', validSpecialtyId],
    queryFn: () => appointmentApi.getDoctors(validSpecialtyId ?? undefined),
    enabled: isAuthenticated,
    staleTime: 5 * 60 * 1000,
  });
}

/**
 * A doctor's availability, optionally for one date.
 * The booking page passes the selected date so the UI shows only windows
 * actually in force that day. Guidance only — the backend re-validates at submit.
 */
export function useDoctorAvailability(doctorId: number | null, date: string | null) {
  const { isAuthenticated } = useAuth();

  const validDoctorId = doctorId !== null && Number.isFinite(doctorId) ? doctorId : null;

  return useQuery({
    queryKey: ['doctorAvailability', validDoctorId, date],
    queryFn: () => appointmentApi.getDoctorAvailability(validDoctorId!, date ?? undefined),
    enabled: isAuthenticated && validDoctorId !== null,
    staleTime: 60 * 1000,
  });
}

/**
 * Shared invalidation for doctor lifecycle transitions: the detail view, the list
 * and the dashboards all read from these keys, so updating them is enough.
 */
function useTransitionMutation(
  action: 'confirm' | 'complete' | 'no-show',
) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: number) =>
      action === 'confirm'
        ? appointmentApi.confirmAppointment(id)
        : action === 'complete'
          ? appointmentApi.completeAppointment(id)
          : appointmentApi.markAppointmentNoShow(id),
    onSuccess: (response) => {
      queryClient.invalidateQueries({ queryKey: ['appointments'] });
      queryClient.setQueryData(['appointment', response.id], response);
    },
  });
}

/** Doctor action: SCHEDULED → CONFIRMED. */
export function useConfirmAppointment() {
  return useTransitionMutation('confirm');
}

/** Doctor action: CONFIRMED → COMPLETED. */
export function useCompleteAppointment() {
  return useTransitionMutation('complete');
}

/** Doctor action: CONFIRMED → NO_SHOW. */
export function useNoShowAppointment() {
  return useTransitionMutation('no-show');
}
