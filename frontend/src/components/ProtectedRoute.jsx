import { Navigate, Outlet, useLocation } from 'react-router-dom';

import { LoadingBlock } from './Spinner.jsx';
import { SessionStatus, useSessionStore } from '../state/session.js';

/**
 * Route guard.
 *
 * Three states, not two: while the session is still being resolved the app shows a loading state rather
 * than redirecting, because redirecting an authenticated user to the login screen on every reload is the
 * classic bug that made this distinction necessary.
 */
export function ProtectedRoute({ requireCompleteProfile = true }) {
  const status = useSessionStore((state) => state.status);
  const user = useSessionStore((state) => state.user);
  const location = useLocation();

  if (status === SessionStatus.UNKNOWN) {
    return <LoadingBlock label="Checking your session…" />;
  }

  if (status === SessionStatus.ANONYMOUS) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  if (requireCompleteProfile && user?.profileComplete === false && location.pathname !== '/onboarding') {
    return <Navigate to="/onboarding" replace />;
  }

  return <Outlet />;
}

export function PublicOnlyRoute() {
  const status = useSessionStore((state) => state.status);

  if (status === SessionStatus.UNKNOWN) {
    return <LoadingBlock label="Checking your session…" />;
  }
  if (status === SessionStatus.AUTHENTICATED) {
    return <Navigate to="/" replace />;
  }
  return <Outlet />;
}

export default ProtectedRoute;
