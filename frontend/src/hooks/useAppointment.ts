import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import * as appointmentApi from '../api/appointmentApi';
import type { CreateAppointmentRequest, CancelAppointmentRequest } from '../types/appointment';
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
    staleTime: Infinity,
  });
}

/**
 * Active doctors, optionally narrowed to one specialty.
 * The specialtyId is part of the query key, so switching specialties in the
 * booking form fetches exactly the doctors of that specialty.
 */
export function useDoctors(specialtyId: number | null) {
  const { isAuthenticated } = useAuth();

  return useQuery({
    queryKey: ['doctors', specialtyId],
    queryFn: () => appointmentApi.getDoctors(specialtyId ?? undefined),
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

  return useQuery({
    queryKey: ['doctorAvailability', doctorId, date],
    queryFn: () => appointmentApi.getDoctorAvailability(doctorId!, date ?? undefined),
    enabled: isAuthenticated && doctorId !== null,
    staleTime: 60 * 1000,
  });
}
