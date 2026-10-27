import { useQuery } from '@tanstack/react-query';
import * as appointmentApi from '../api/appointmentApi';
import { useAuth } from './useAuth';

export function useMyAppointments() {
  const { isAuthenticated } = useAuth();

  return useQuery({
    queryKey: ['appointments', 'me'],
    queryFn: appointmentApi.getMyAppointments,
    enabled: isAuthenticated,
    retry: 1,
  });
}
