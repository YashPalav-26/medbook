

const TOKEN_KEY = 'medbook.token';
const USER_KEY = 'medbook.user';

export interface StoredUser {
  id: number;
  email: string;
  firstName: string;
  lastName: string;
  roles: string[];
}

export function saveAuth(token: string, user: StoredUser): void {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(USER_KEY, JSON.stringify(user));
}

export function getStoredToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function getStoredUser(): StoredUser | null {
  const raw = localStorage.getItem(USER_KEY);
  if (!raw) {
    return null;
  }
  try {
    const parsed = JSON.parse(raw) as StoredUser;
    if (typeof parsed?.id !== 'number' || !Array.isArray(parsed?.roles)) {
      throw new Error('unexpected shape');
    }
    return parsed;
  } catch {

    clearAuth();
    return null;
  }
}

export function clearAuth(): void {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}
