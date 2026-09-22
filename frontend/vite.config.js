import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

/**
 * Vite configuration.
 *
 * The dev server proxies /api and /ws to the backend so the browser only ever talks to one origin.
 * That is not a convenience: it keeps the session cookie first-party and SameSite=Lax valid in
 * development, so the local setup behaves like production instead of relying on CORS to paper over a
 * cross-origin cookie problem that would only appear after deployment.
 */
/*
 * The proxy is shared by `vite dev` and `vite preview`.
 *
 * `preview` serves the built bundle, which loads in a handful of requests instead of one per source
 * module — on a slow connection that is the difference between "the UI is sluggish" and "the UI is
 * instant". It is the fastest way to review the interface locally, so it needs the same one-origin
 * routing as the dev server.
 */
const backendTarget = process.env.VITE_BACKEND_URL || 'http://localhost:8080';

const proxy = {
  '/api': {
    target: backendTarget,
    changeOrigin: true,
    // Without a proxy timeout, a backend that is not running leaves the browser waiting on a socket
    // that may never answer, and the app appears to hang while "checking the session". Failing after a
    // few seconds produces a clear error the UI can show instead.
    timeout: 10000,
    proxyTimeout: 10000,
  },
  '/ws': { target: backendTarget, ws: true, changeOrigin: true },
  '/actuator': { target: backendTarget, changeOrigin: true },
};

export default defineConfig({
  plugins: [react()],
  preview: {
    host: true,
    port: 4173,
    strictPort: true,
    allowedHosts: true,
    proxy,
  },
  server: {
    host: true, // bind 0.0.0.0 so the dev server is reachable from a container or preview host
    port: 5173,
    strictPort: true,
    // The dev server is reached through a proxy whose Host header is not localhost (preview URLs,
    // containers, tunnels). Rejecting those would make the app look broken; this is a development
    // convenience only — production traffic is terminated by nginx, not by Vite.
    allowedHosts: true,
    proxy,
  },
  build: {
    outDir: 'dist',
    sourcemap: true,
    // Split the realtime client out of the main bundle: the socket is only needed after login, so a
    // visitor who never signs in should not download it. Rolldown (Vite 8) takes a function form.
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('node_modules/@stomp/stompjs')) return 'realtime';
          if (id.includes('node_modules/react-router')) return 'router';
          return undefined;
        },
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.js'],
    css: true,
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html'],
      exclude: ['src/test/**', 'src/**/*.test.jsx', 'src/main.jsx'],
    },
  },
});
