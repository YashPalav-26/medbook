import { createContext, useCallback, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import type { AuthResponse, AuthUser, LoginRequest, RegisterRequest } from '../types/auth';
import { clearAuth, getStoredToken, getStoredUser, saveAuth } from '../lib/storage';
import * as authApi from '../api/authApi';







export interface AuthContextValue {
  user: AuthUser | null;
  token: string | null;
  isAuthenticated: boolean;
  login: (input: LoginRequest) => Promise<void>;
  register: (input: RegisterRequest) => Promise<void>;
  logout: () => void;

  updateUser: (patch: Partial<Pick<AuthUser, 'firstName' | 'lastName'>>) => void;
}

export interface AuthContextType {
  authContextValue: AuthContextValue;
}

export const AuthContext = createContext<AuthContextValue | undefined>(undefined);


function applyAuthResponse(response: AuthResponse): AuthUser {
  saveAuth(response.token, response.user);
  return response.user;
}

export function AuthProvider({ children }: { children: ReactNode }) {

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


  const updateUser = useCallback(
    (patch: Partial<Pick<AuthUser, 'firstName' | 'lastName'>>) => {
      setUser((current) => {
        if (!current) return current;
        const next = { ...current, ...patch };

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
