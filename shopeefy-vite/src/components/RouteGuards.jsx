import { useSelector } from 'react-redux';
import { Navigate, Outlet, useLocation } from 'react-router';
import { selectAuthReady, selectUser } from '../store/authSlice';
import { safeReturnPath } from '../utils/navigation';
import { Loading } from './Feedback';

export function RequireAuth() {
  const ready = useSelector(selectAuthReady);
  const user = useSelector(selectUser);
  const location = useLocation();
  if (!ready) return <Loading label="Restoring your session…" />;
  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  }
  return <Outlet />;
}

/**
 * Hides the admin area from customers. This is only for the UI: every /api/admin call is
 * authorised again on the server from the signed token.                     [OWASP A01:2025]
 */
export function RequireAdmin() {
  const ready = useSelector(selectAuthReady);
  const user = useSelector(selectUser);
  const location = useLocation();
  if (!ready) return <Loading label="Restoring your session…" />;
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  if (user.role !== 'ADMIN') return <Navigate to="/" replace />;
  return <Outlet />;
}

/** Sign-in and sign-up pages: a signed-in user goes straight on to where they were heading. */
export function GuestOnly() {
  const ready = useSelector(selectAuthReady);
  const user = useSelector(selectUser);
  const location = useLocation();
  if (!ready) return <Loading label="Restoring your session…" />;
  if (user) return <Navigate to={safeReturnPath(location.state?.from)} replace />;
  return <Outlet />;
}
