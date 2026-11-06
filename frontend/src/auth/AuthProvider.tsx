import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { configureClient } from '../api/client';
import { authApi } from '../api/endpoints';
import type { User } from '../api/types';
import { AuthContext, type AuthState } from './useAuth';

/**
 * Session state. The token lives in sessionStorage: it survives a page reload but not closing
 * the tab, and never leaves the browser except in the Authorization header. Combined with the
 * strict CSP served by Nginx this keeps the token's exposure small without needing cookies
 * (and therefore without CSRF protection).
 */
interface Session {
  token: string;
  expiresAt: string;
  user: User;
}

const STORAGE_KEY = 'examgrid.session';

function readSession(): Session | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const session = JSON.parse(raw) as Session;
    return new Date(session.expiresAt).getTime() > Date.now() ? session : null;
  } catch {
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(readSession);

  const logout = useCallback(() => {
    sessionStorage.removeItem(STORAGE_KEY);
    setSession(null);
  }, []);

  // Configure synchronously on every render so the very first queries carry the token.
  configureClient(() => session?.token ?? null, logout);

  useEffect(() => {
    if (!session) return;
    const remaining = new Date(session.expiresAt).getTime() - Date.now();
    const timer = window.setTimeout(logout, Math.max(0, remaining));
    return () => window.clearTimeout(timer);
  }, [session, logout]);

  const login = useCallback(async (username: string, password: string) => {
    const response = await authApi.login(username, password);
    const next: Session = { token: response.accessToken, expiresAt: response.expiresAt, user: response.user };
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(next));
    setSession(next);
    return response.user;
  }, []);

  const value = useMemo<AuthState>(
    () => ({
      user: session?.user ?? null,
      token: session?.token ?? null,
      login,
      logout,
      hasRole: (...roles) => !!session && roles.includes(session.user.role),
    }),
    [session, login, logout],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
