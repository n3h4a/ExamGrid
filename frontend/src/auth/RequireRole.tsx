import { Navigate, Outlet, useLocation } from 'react-router';
import type { Role } from '../api/types';
import { homeFor, useAuth } from './useAuth';

/** Route guard: unauthenticated users go to /login, users of another role to their home. */
export function RequireRole({ roles }: { roles: Role[] }) {
  const { user } = useAuth();
  const location = useLocation();
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  if (!roles.includes(user.role)) return <Navigate to={homeFor(user.role)} replace />;
  return <Outlet />;
}
