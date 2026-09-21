import { useQuery } from '@tanstack/react-query';
import * as appointmentApi from '../api/appointmentApi';
import { useAuth } from './useAuth';

/**
 * The authenticated caller's appointments (GET /api/v1/appointments/me).
 *
 * Only runs while signed in; 401s are handled globally by the Axios interceptor
 * (clear auth → /login), 403s surface through the query's error state.
 */
export function useMyAppointments() {
  const { isAuthenticated } = useAuth();

  return useQuery({
    queryKey: ['appointments', 'me'],
    queryFn: appointmentApi.getMyAppointments,
    enabled: isAuthenticated,
    retry: 1,
  });
}
