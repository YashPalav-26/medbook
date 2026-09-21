/**
 * JWT + user persistence.
 *
 * Storage choice: window.localStorage, deliberately, for this learning project.
 *
 * Trade-off (documented, not hidden): localStorage is readable by any JavaScript running on
 * the page, so a successful XSS attack could steal the token. Production-grade alternatives
 * (HttpOnly cookie sessions or in-memory tokens with refresh rotation) need backend support
 * the API does not have yet — there is no refresh-token or CSRF flow to pair with cookies.
 * Consequences to remember:
 *   - the token survives page refreshes and new tabs (this is what we want here),
 *   - "logout" in another tab will not update this tab until it reloads,
 *   - anything XSS-shaped becomes an authentication problem, not just a content problem.
 *
 * Only the access token and the user's own public profile are stored. Never store: the
 * plaintext password, the password hash, the JWT signing secret, or database credentials.
 */

const TOKEN_KEY = 'medbook.token';
const USER_KEY = 'medbook.user';

/** Minimal non-sensitive user information the UI needs. */
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
    // Corrupt or outdated data: drop it instead of rendering a broken session.
    clearAuth();
    return null;
  }
}

export function clearAuth(): void {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}
