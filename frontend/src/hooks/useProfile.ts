import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import * as profileApi from '../api/profileApi';
import { useAuth } from './useAuth';
import type {
  AccountUpdateRequest,
  PatientProfileUpdateRequest,
  DoctorProfileUpdateRequest,
  ChangePasswordRequest,
} from '../types/auth';

export function useMyProfile() {
  const { isAuthenticated } = useAuth();

  return useQuery({
    queryKey: ['me', 'profile'],
    queryFn: profileApi.getMyProfile,
    enabled: isAuthenticated,
  });
}

export function useUpdateAccount() {
  const queryClient = useQueryClient();
  const { updateUser } = useAuth();

  return useMutation({
    mutationFn: (payload: AccountUpdateRequest) => profileApi.updateMyAccount(payload),
    onSuccess: (updated) => {
      queryClient.setQueryData(['me', 'profile'], updated);
      updateUser({ firstName: updated.firstName, lastName: updated.lastName });
    },
  });
}

export function useUpdatePatientProfile() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: PatientProfileUpdateRequest) => profileApi.updateMyPatientProfile(payload),
    onSuccess: (updated) => {
      queryClient.setQueryData(['me', 'profile'], updated);
    },
  });
}

export function useUpdateDoctorProfile() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: DoctorProfileUpdateRequest) => profileApi.updateMyDoctorProfile(payload),
    onSuccess: (updated) => {
      queryClient.setQueryData(['me', 'profile'], updated);
    },
  });
}

export function useChangePassword() {
  return useMutation({
    mutationFn: (payload: ChangePasswordRequest) => profileApi.changeMyPassword(payload),
  });
}
