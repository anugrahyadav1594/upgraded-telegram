import { useEffect } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';

import { ErrorBoundary } from './components/ErrorBoundary.jsx';
import { NavRail } from './components/NavRail.jsx';
import { ProtectedRoute, PublicOnlyRoute } from './components/ProtectedRoute.jsx';
import { Toasts } from './components/Toasts.jsx';
import { ChatPage } from './pages/ChatPage.jsx';
import { ForgotPasswordPage } from './pages/ForgotPasswordPage.jsx';
import { LoginPage } from './pages/LoginPage.jsx';
import { NotFoundPage } from './pages/NotFoundPage.jsx';
import { NotificationsPage } from './pages/NotificationsPage.jsx';
import { OnboardingPage } from './pages/OnboardingPage.jsx';
import { ProfilePage } from './pages/ProfilePage.jsx';
import { RegisterPage } from './pages/RegisterPage.jsx';
import { ResetPasswordPage } from './pages/ResetPasswordPage.jsx';
import { SettingsPage } from './pages/SettingsPage.jsx';
import { useRealtime } from './hooks/useRealtime.js';
import { useThemeStore, watchSystemTheme } from './state/theme.js';
import { SessionStatus, useSessionStore } from './state/session.js';
import { useNotificationStore } from './state/notifications.js';
import { useRealtimeStore } from './state/realtime.js';
import { resetRealtimeClient } from './websocket/stompClient.js';

/**
 * Application shell.
 *
 * The session is resolved once, before anything else renders, so the app never shows a login screen to a
 * signed-in user or vice versa. The realtime bridge is mounted only for authenticated sessions — an
 * anonymous visitor has no socket, and a lost session tears it down instead of retrying forever.
 */
function AuthenticatedShell({ children }) {
  const status = useSessionStore((state) => state.status);
  const logout = useSessionStore((state) => state.logout);
  const pushToast = useNotificationStore((state) => state.pushToast);
  const resetNotifications = useNotificationStore((state) => state.reset);
  const resetRealtime = useRealtimeStore((state) => state.reset);

  useRealtime({
    onSessionRevoked: () => {
      pushToast({
        title: 'Signed out',
        body: 'This session was ended elsewhere, or your account was suspended.',
        tone: 'warning',
        sticky: true,
      });
    },
  });

  async function signOut() {
    await logout();
    resetRealtimeClient();
    resetNotifications();
    resetRealtime();
  }

  return (
    <div className="app-shell">
      <NavRail onSignOut={signOut} />
      <div className={status === SessionStatus.AUTHENTICATED ? 'app-shell__main' : ''}>{children}</div>
    </div>
  );
}

export function App() {
  const bootstrap = useSessionStore((state) => state.bootstrap);
  const status = useSessionStore((state) => state.status);
  const applyTheme = useThemeStore((state) => state.apply);

  // Session first: every route decision depends on it.
  useEffect(() => {
    bootstrap();
  }, [bootstrap]);

  // Theme before/with first paint, plus a live listener for the "system" choice.
  useEffect(() => {
    applyTheme();
    return watchSystemTheme();
  }, [applyTheme]);

  return (
    <ErrorBoundary>
      <Routes>
        <Route element={<PublicOnlyRoute />}>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/forgot-password" element={<ForgotPasswordPage />} />
          <Route path="/reset-password" element={<ResetPasswordPage />} />
        </Route>

        <Route element={<ProtectedRoute />}>
          <Route
            path="/"
            element={
              <AuthenticatedShell>
                <ChatPage />
              </AuthenticatedShell>
            }
          />
          <Route
            path="/c/:conversationId"
            element={
              <AuthenticatedShell>
                <ChatPage />
              </AuthenticatedShell>
            }
          />
          <Route
            path="/u/:username"
            element={
              <AuthenticatedShell>
                <ProfilePage />
              </AuthenticatedShell>
            }
          />
          <Route
            path="/notifications"
            element={
              <AuthenticatedShell>
                <NotificationsPage />
              </AuthenticatedShell>
            }
          />
          <Route
            path="/settings"
            element={
              <AuthenticatedShell>
                <SettingsPage />
              </AuthenticatedShell>
            }
          />
        </Route>

        <Route element={<ProtectedRoute requireCompleteProfile={false} />}>
          <Route path="/onboarding" element={<OnboardingPage />} />
        </Route>

        <Route path="/app" element={<Navigate to="/" replace />} />
        <Route path="*" element={<NotFoundPage />} />
      </Routes>

      {status !== SessionStatus.UNKNOWN ? <Toasts /> : null}
    </ErrorBoundary>
  );
}

export default App;
