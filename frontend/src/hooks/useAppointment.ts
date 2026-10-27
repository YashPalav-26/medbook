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

export function useSpecialties() {
  const { isAuthenticated } = useAuth();

  return useQuery({
    queryKey: ['specialties'],
    queryFn: appointmentApi.getSpecialties,
    enabled: isAuthenticated,

    staleTime: 60 * 1000,
  });
}

export function useDoctors(specialtyId: number | null) {
  const { isAuthenticated } = useAuth();

  const validSpecialtyId = specialtyId !== null && Number.isFinite(specialtyId) ? specialtyId : null;

  return useQuery({
    queryKey: ['doctors', validSpecialtyId],
    queryFn: () => appointmentApi.getDoctors(validSpecialtyId ?? undefined),
    enabled: isAuthenticated,
    staleTime: 5 * 60 * 1000,
  });
}

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

export function useConfirmAppointment() {
  return useTransitionMutation('confirm');
}

export function useCompleteAppointment() {
  return useTransitionMutation('complete');
}

export function useNoShowAppointment() {
  return useTransitionMutation('no-show');
}
