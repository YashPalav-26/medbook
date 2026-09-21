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