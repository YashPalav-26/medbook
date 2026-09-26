import { createContext, useCallback, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import type { AuthResponse, AuthUser, LoginRequest, RegisterRequest } from '../types/auth';
import { clearAuth, getStoredToken, getStoredUser, saveAuth } from '../lib/storage';
import * as authApi from '../api/authApi';

/**
 * Authentication state for the whole app (Context + hooks, no Redux).
 *
 * State is initialized from localStorage so a page refresh keeps the user signed in, and
 * login/register/logout keep both the context and the stored copy in sync.
 */
export interface AuthContextValue {
  user: AuthUser | null;
  token: string | null;
  isAuthenticated: boolean;
  login: (input: LoginRequest) => Promise<void>;
  register: (input: RegisterRequest) => Promise<void>;
  logout: () => void;
  /** Merge fields into the signed-in user (e.g. a new name after a profile update). */
  updateUser: (patch: Partial<Pick<AuthUser, 'firstName' | 'lastName'>>) => void;
}

export interface AuthContextType {
  authContextValue: AuthContextValue;
}

export const AuthContext = createContext<AuthContextValue | undefined>(undefined);

/** Persist the token/user, then return the user for the context state. */
function applyAuthResponse(response: AuthResponse): AuthUser {
  saveAuth(response.token, response.user);
  return response.user;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  // Restore the session from storage on first render (survives page refreshes).
  const [token, setToken] = useState<string | null>(() => getStoredToken());
  const [user, setUser] = useState<AuthUser | null>(() => getStoredUser());

  const login = useCallback(async (input: LoginRequest): Promise<void> => {
    const response = await authApi.login(input);
    setUser(applyAuthResponse(response));
    setToken(response.token);
  }, []);

  const register = useCallback(async (input: RegisterRequest): Promise<void> => {
    const response = await authApi.register(input);
    setUser(applyAuthResponse(response));
    setToken(response.token);
  }, []);

  const logout = useCallback(() => {
    clearAuth();
    setToken(null);
    setUser(null);
  }, []);

  /** Merge a patch into the user state and the stored copy (header shows the new name). */
  const updateUser = useCallback(
    (patch: Partial<Pick<AuthUser, 'firstName' | 'lastName'>>) => {
      setUser((current) => {
        if (!current) return current;
        const next = { ...current, ...patch };
        // Keep the stored copy in step so a refresh keeps the updated name too.
        const storedToken = getStoredToken();
        if (storedToken) saveAuth(storedToken, next);
        return next;
      });
    },
    [],
  );

  const value = useMemo<AuthContextValue>(
    () => ({ user, token, isAuthenticated: Boolean(token && user), login, register, logout, updateUser }),
    [user, token, login, register, logout, updateUser],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
