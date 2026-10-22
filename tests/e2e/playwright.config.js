import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright configuration.
 *
 * Two settings carry real weight:
 *
 *  * **Trace/ video on first retry.** A failing end-to-end run is usually a timing or realtime-ordering
 *    problem, and a trace shows exactly which frame never arrived. Fighting it from log text alone is a
 *    waste of hours.
 *  * **Fully parallel workers with no shared state.** Every spec creates its own accounts with random
 *    suffixes, so tests never depend on each other's data or on execution order.
 */
export default defineConfig({
  testDir: '.',
  timeout: 60_000,
  expect: { timeout: 15_000 },
  fullyParallel: true,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  workers: process.env.CI ? 2 : undefined,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : [['list']],
  use: {
    baseURL: process.env.BASE_URL || 'http://localhost:8080',
    trace: 'on-first-retry',
    video: 'retain-on-failure',
    screenshot: 'only-on-failure',
    // A locale and timezone are pinned so relative-time assertions ("Just now", "2 minutes ago") are
    // stable wherever the suite runs.
    locale: 'en-GB',
    timezoneId: 'UTC',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
    { name: 'mobile-safari', use: { ...devices['iPhone 13'] } },
  ],
});
