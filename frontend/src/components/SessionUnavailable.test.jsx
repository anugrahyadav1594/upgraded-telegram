import { describe, expect, it, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

import { ApiError } from '../api/client.js';
import { PublicOnlyRoute } from './ProtectedRoute.jsx';
import { SessionStatus, useSessionStore } from '../state/session.js';

/**
 * Regression guard for the "spinner that never ends" experience: an unreachable backend must produce
 * something the user can read and act on, on both the public and the protected routes.
 */
describe('unreachable backend', () => {
  beforeEach(() => {
    useSessionStore.setState({
      status: SessionStatus.UNKNOWN,
      user: null,
      error: new ApiError({
        status: 0,
        code: 'NETWORK_ERROR',
        message: 'Cannot reach the server. Check your connection and try again.',
      }),
    });
  });

  function renderPublicRoute() {
    return render(
      <MemoryRouter initialEntries={['/login']}>
        <Routes>
          <Route element={<PublicOnlyRoute />}>
            <Route path="/login" element={<div>the login form</div>} />
          </Route>
        </Routes>
      </MemoryRouter>,
    );
  }

  it('explains the problem instead of showing the sign-in form', () => {
    renderPublicRoute();

    expect(screen.getByRole('heading', { name: /reach the server/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /try again/i })).toBeInTheDocument();
    expect(screen.queryByText('the login form')).not.toBeInTheDocument();
  });

  it('tells the user what to start and where the API is expected', () => {
    renderPublicRoute();

    expect(screen.getByText(/is the api running/i)).toBeInTheDocument();
    expect(screen.getByText(/mvnw spring-boot:run/)).toBeInTheDocument();
  });

  it('shows the spinner only while the outcome is genuinely unknown', () => {
    useSessionStore.setState({ status: SessionStatus.UNKNOWN, error: null });
    renderPublicRoute();

    expect(screen.queryByRole('heading', { name: /reach the server/i })).not.toBeInTheDocument();
    expect(screen.getByRole('progressbar', { name: /checking your session/i })).toBeInTheDocument();
  });
});
