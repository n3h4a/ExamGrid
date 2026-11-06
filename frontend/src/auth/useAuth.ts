import { createContext, useContext } from 'react';
import type { Role, User } from '../api/types';

export interface AuthState {
  user: User | null;
  token: string | null;
  login: (username: string, password: string) => Promise<User>;
  logout: () => void;
  hasRole: (...roles: Role[]) => boolean;
}

export const AuthContext = createContext<AuthState | null>(null);

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>');
  return ctx;
}

/** Landing page for each role after login. */
export function homeFor(role: Role): string {
  switch (role) {
    case 'EXAM_CELL':
      return '/admin';
    case 'FACULTY':
      return '/duties';
    case 'STUDENT':
      return '/me';
  }
}
