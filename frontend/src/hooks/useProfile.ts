import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import * as profileApi from '../api/profileApi';
import { useAuth } from './useAuth';
import type {
  AccountUpdateRequest,
  PatientProfileUpdateRequest,
  DoctorProfileUpdateRequest,
  ChangePasswordRequest,
} from '../types/auth';

/**
 * React Query hooks for the own-profile page.
 *
 * Query family: ['me', ...]. The profile query is the source of truth for the page;
 * account updates also refresh the AuthContext copy (the sidebar/header read it) via
 * the mutation's onSuccess sync below — no duplicate fetching, no parallel state.
 */

/** The caller's own profile (account + role-specific block). */
export function useMyProfile() {
  const { isAuthenticated } = useAuth();

  return useQuery({
    queryKey: ['me', 'profile'],
    queryFn: profileApi.getMyProfile,
    enabled: isAuthenticated,
  });
}

/**
 * Account update (name + phone). On success the profile cache is refreshed AND the
 * AuthContext user copy is synced so the header/sidebar show the new name immediately.
 */
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

/** Patient profile update (self-service fields; the backend rejects non-patients with 403). */
export function useUpdatePatientProfile() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: PatientProfileUpdateRequest) => profileApi.updateMyPatientProfile(payload),
    onSuccess: (updated) => {
      queryClient.setQueryData(['me', 'profile'], updated);
    },
  });
}

/** Doctor profile update (bio + picture; specialty/license/fee stay admin-controlled). */
export function useUpdateDoctorProfile() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: DoctorProfileUpdateRequest) => profileApi.updateMyDoctorProfile(payload),
    onSuccess: (updated) => {
      queryClient.setQueryData(['me', 'profile'], updated);
    },
  });
}

/** Password change. The token model is stateless: the current JWT stays valid until expiry. */
export function useChangePassword() {
  return useMutation({
    mutationFn: (payload: ChangePasswordRequest) => profileApi.changeMyPassword(payload),
  });
}
