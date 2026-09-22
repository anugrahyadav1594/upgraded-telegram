import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

import { App } from './App.jsx';
import { ApiError } from './api/client.js';

import './styles/tokens.css';
import './styles/base.css';
import './styles/components.css';
import './styles/chat.css';
import './styles/auth.css';

/**
 * React Query configuration.
 *
 * The defaults encode the product's tolerance for staleness: refetch on focus and reconnect (a chat that
 * shows yesterday's messages after a laptop wakes is broken), but never retry a 4xx — a rejected request
 * will be rejected again, and retrying it burns rate-limit budget.
 */
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: true,
      refetchOnReconnect: true,
      retry: (failureCount, error) => {
        if (error instanceof ApiError) {
          if (!error.isRetryable) return false;
          return failureCount < 2;
        }
        return failureCount < 1;
      },
      staleTime: 10_000,
    },
    mutations: {
      retry: false,
    },
  },
});

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
);
