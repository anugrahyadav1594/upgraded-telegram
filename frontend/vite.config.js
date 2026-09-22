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
export default defineConfig({
  plugins: [react()],
  server: {
    host: true, // bind 0.0.0.0 so the dev server is reachable from a container or preview host
    port: 5173,
    strictPort: true,
    // The dev server is reached through a proxy whose Host header is not localhost (preview URLs,
    // containers, tunnels). Rejecting those would make the app look broken; this is a development
    // convenience only — production traffic is terminated by nginx, not by Vite.
    allowedHosts: true,
    proxy: {
      '/api': {
        target: process.env.VITE_BACKEND_URL || 'http://localhost:8080',
        changeOrigin: true,
      },
      '/ws': {
        target: process.env.VITE_BACKEND_URL || 'http://localhost:8080',
        ws: true,
        changeOrigin: true,
      },
      '/actuator': {
        target: process.env.VITE_BACKEND_URL || 'http://localhost:8080',
        changeOrigin: true,
      },
    },
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
