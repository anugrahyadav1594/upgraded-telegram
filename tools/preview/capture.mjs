/**
 * Screenshot capture for the README and the documentation.
 *
 * Drives a real browser against the running frontend and writes PNGs to `docs/screenshots/`. It is a
 * documentation tool, not a test: it asserts only that the screens it photographs actually rendered
 * (a missing selector fails the run) so a broken build cannot produce a pretty picture of nothing.
 *
 * ## Usage
 *
 *     node tools/preview/server.js            # terminal 1 — API stub (see the warning below)
 *     cd frontend && npm run dev              # terminal 2 — the app on :5173
 *     node tools/preview/capture.mjs          # terminal 3 — writes docs/screenshots/*.png
 *
 * ## Two honest caveats
 *
 * 1. **These are not screenshots of the production backend.** The API stub answers; the Java backend
 *    is the real thing and produces the same screens with real data. Anything generated here is for
 *    layout and design review, and is labelled as such in the README.
 * 2. **The browser is found, not bundled.** `BROWSER_PATH` (or `CHROME_PATH`) wins; otherwise the
 *    usual Chrome/Edge locations are tried. On a machine with neither, `npm i @sparticuz/chromium`
 *    provides one — that is how the images in this repository were produced on a headless Linux box
 *    with no browser installed.
 */

import { mkdir, writeFile } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(HERE, '..', '..');
const OUTPUT_DIR = path.join(REPO_ROOT, 'docs', 'screenshots');
const APP_URL = process.env.APP_URL || 'http://localhost:5173';
const API_URL = process.env.PREVIEW_URL || 'http://localhost:8080';
const SESSION_COOKIE = 'preview_session';

const DESKTOP = { width: 1440, height: 900, deviceScaleFactor: 2 };
const MOBILE = { width: 390, height: 844, deviceScaleFactor: 3, isMobile: true, hasTouch: true };

const CONVERSATION_ID = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
const PEER_ID = '22222222-2222-4222-8222-222222222222';

// ---------------------------------------------------------------------------------------
// Browser discovery
// ---------------------------------------------------------------------------------------
function findBrowser() {
  const candidates = [
    process.env.BROWSER_PATH,
    process.env.CHROME_PATH,
    // Windows
    'C:/Program Files/Google/Chrome/Application/chrome.exe',
    'C:/Program Files (x86)/Google/Chrome/Application/chrome.exe',
    'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
    // macOS
    '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
    '/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge',
    // Linux
    '/usr/bin/google-chrome',
    '/usr/bin/google-chrome-stable',
    '/usr/bin/chromium',
    '/usr/bin/chromium-browser',
    '/usr/bin/microsoft-edge',
    // Lambda-style bundle, if someone installed it
    path.join(HERE, 'node_modules', '@sparticuz', 'chromium'),
  ].filter(Boolean);

  for (const candidate of candidates) {
    if (candidate.endsWith('@sparticuz/chromium')) {
      if (existsSync(candidate)) return { kind: 'sparticuz' };
      continue;
    }
    if (existsSync(candidate)) return { kind: 'path', path: candidate };
  }
  return null;
}

async function launchBrowser() {
  let puppeteer;
  try {
    puppeteer = (await import('puppeteer-core')).default;
  } catch {
    console.error('puppeteer-core is required:\n  cd tools/preview && npm install\n');
    process.exit(1);
  }

  const found = findBrowser();
  if (!found) {
    console.error(
      'No browser found. Install Chrome/Edge, or set BROWSER_PATH, or run:\n' +
        '  cd tools/preview && npm install --no-save @sparticuz/chromium\n',
    );
    process.exit(1);
  }

  const args = [
    '--no-sandbox',
    '--disable-setuid-sandbox',
    '--disable-dev-shm-usage',
    '--font-render-hinting=none',
    '--force-device-scale-factor=1',
  ];

  if (found.kind === 'sparticuz') {
    const chromium = (await import('@sparticuz/chromium')).default;
    const executablePath = await chromium.executablePath();
    return puppeteer.launch({ args: [...args, ...chromium.args], executablePath, headless: true });
  }
  return puppeteer.launch({ args, executablePath: found.path, headless: true });
}

// ---------------------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------------------
async function api(pathname, options = {}) {
  const response = await fetch(`${API_URL}${pathname}`, {
    method: options.method || 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: options.body ? JSON.stringify(options.body) : undefined,
  });
  return response.json().catch(() => ({}));
}

async function openPage(browser, { viewport = DESKTOP, theme = 'light', signedIn = true } = {}) {
  const page = await browser.newPage();
  await page.setViewport(viewport);
  await page.setExtraHTTPHeaders({ 'Accept-Language': 'en-GB' });

  if (signedIn) {
    await page.setCookie({ name: SESSION_COOKIE, value: '1', domain: 'localhost', path: '/' });
  } else {
    await page.deleteCookie({ name: SESSION_COOKIE, domain: 'localhost', path: '/' });
  }

  // Theme is applied before first paint, exactly as the app's own bootstrap does it.
  await page.evaluateOnNewDocument((value) => {
    try {
      localStorage.setItem('realchat.theme', value);
    } catch {
      /* storage disabled — the default theme is used, which is fine for a screenshot */
    }
  }, theme);

  page.on('pageerror', (error) => console.warn(`  ! page error: ${error.message}`));
  return page;
}

async function shoot(page, name, { fullPage = false, selector } = {}) {
  if (selector) await page.waitForSelector(selector, { timeout: 15000 });
  // Let fonts, the theme transition and any React Query refetch settle — otherwise a screenshot can
  // catch the app mid-render, which looks like a bug in the documentation.
  await new Promise((resolve) => setTimeout(resolve, 450));
  const file = path.join(OUTPUT_DIR, `${name}.png`);
  await page.screenshot({ path: file, fullPage });
  console.log(`  → ${path.relative(REPO_ROOT, file)}`);
  return file;
}

const shots = [];

// ---------------------------------------------------------------------------------------
// The shoot list. Each entry is a scene; the name describes the file it writes.
// ---------------------------------------------------------------------------------------
async function capture(browser) {
  await mkdir(OUTPUT_DIR, { recursive: true });
  await api('/__preview/reset', { body: {} });

  // --- unauthenticated ---------------------------------------------------------------
  {
    const page = await openPage(browser, { signedIn: false });
    await page.goto(`${APP_URL}/login`, { waitUntil: 'networkidle2' });
    shots.push(await shoot(page, '01-sign-in-light', { selector: 'form' }));

    await page.goto(`${APP_URL}/register`, { waitUntil: 'networkidle2' });
    shots.push(await shoot(page, '02-create-account-light', { selector: 'form' }));

    await page.goto(`${APP_URL}/forgot-password`, { waitUntil: 'networkidle2' });
    shots.push(await shoot(page, '03-password-reset-light', { selector: 'form' }));
    await page.close();
  }

  // --- first run (empty state) --------------------------------------------------------
  {
    await api('/__preview/reset', { body: { empty: true } });
    const page = await openPage(browser, { signedIn: true });
    await page.goto(APP_URL, { waitUntil: 'networkidle2' });
    shots.push(await shoot(page, '04-empty-conversations-light', { selector: '.conversation-pane' }));
    await page.close();
    await api('/__preview/reset', { body: {} });
  }

  // --- the chat surface, light and dark ------------------------------------------------
  {
    const page = await openPage(browser, { theme: 'light' });
    await page.goto(`${APP_URL}/c/${CONVERSATION_ID}`, { waitUntil: 'networkidle2' });
    shots.push(await shoot(page, '05-conversation-light', { selector: '.message-list' }));
    await page.close();
  }
  {
    const page = await openPage(browser, { theme: 'dark' });
    await page.goto(`${APP_URL}/c/${CONVERSATION_ID}`, { waitUntil: 'networkidle2' });
    shots.push(await shoot(page, '06-conversation-dark', { selector: '.message-list' }));

    // A live frame: the peer starts typing, then sends. Everything the UI does here is driven by the
    // frames, not by the harness reaching into the DOM.
    await api('/__preview/emit', {
      body: {
        type: 'typing.started',
        payload: {
          conversationId: CONVERSATION_ID,
          userId: PEER_ID,
          displayName: 'Ghost',
          expiresInMs: 6000,
        },
      },
    });
    await new Promise((resolve) => setTimeout(resolve, 350));
    shots.push(await shoot(page, '07-typing-indicator-dark', { selector: '.typing-line' }));

    await api('/__preview/emit', {
      body: {
        type: 'message.new',
        payload: {
          conversationId: CONVERSATION_ID,
          senderId: PEER_ID,
          content: 'Got it — resync works. Nice.',
        },
      },
    });
    await new Promise((resolve) => setTimeout(resolve, 700));
    shots.push(await shoot(page, '08-live-message-dark', { selector: '.message-list' }));
    await page.close();
  }

  // --- the rest of the signed-in surface ------------------------------------------------
  {
    const page = await openPage(browser, { theme: 'light' });
    await page.goto(`${APP_URL}/notifications`, { waitUntil: 'networkidle2' });
    shots.push(await shoot(page, '09-notifications-light', { selector: '.page' }));

    await page.goto(`${APP_URL}/settings`, { waitUntil: 'networkidle2' });
    shots.push(await shoot(page, '10-settings-light', { selector: '.page' }));

    await page.goto(`${APP_URL}/u/ghost`, { waitUntil: 'networkidle2' });
    shots.push(await shoot(page, '11-public-profile-light', { selector: '.profile-header' }));

    await page.goto(APP_URL, { waitUntil: 'networkidle2' });
    await page.getByRole?.('button'); // no-op on puppeteer; kept for symmetry with Playwright
    shots.push(await shoot(page, '12-conversation-list-light', { selector: '.conversation-pane' }));
    await page.close();
  }

  // --- responsive: mobile is a different navigation model, so it gets its own shots -----
  {
    const page = await openPage(browser, { viewport: MOBILE, theme: 'light' });
    await page.goto(APP_URL, { waitUntil: 'networkidle2' });
    shots.push(await shoot(page, '13-mobile-conversation-list'));

    await page.goto(`${APP_URL}/c/${CONVERSATION_ID}`, { waitUntil: 'networkidle2' });
    shots.push(await shoot(page, '14-mobile-conversation'));
    await page.close();
  }
  {
    const page = await openPage(browser, { viewport: MOBILE, theme: 'dark' });
    await page.goto(`${APP_URL}/settings`, { waitUntil: 'networkidle2' });
    shots.push(await shoot(page, '15-mobile-settings-dark'));
    await page.close();
  }

  // --- the unreachable-backend state ----------------------------------------------------
  // Genuinely exercised: the request to the API is aborted, so the screen is produced by the app's
  // own error path rather than by editing the DOM.
  {
    const page = await openPage(browser, { theme: 'light' });
    await page.setRequestInterception(true);
    page.on('request', (request) => {
      // Match on the *path*, not the whole URL: under the Vite dev server the app's own source modules
      // live under /src/api/, and a naive `url.includes('/api/')` aborts them and blanks the page.
      let pathname = '';
      try {
        pathname = new URL(request.url()).pathname;
      } catch {
        pathname = request.url();
      }
      if (pathname.startsWith('/api/')) request.abort();
      else request.continue();
    });
    await page.goto(APP_URL, { waitUntil: 'domcontentloaded' });
    await page.waitForSelector('h1', { timeout: 20000 });
    shots.push(await shoot(page, '16-server-unreachable-light'));
    await page.close();
  }
}

// ---------------------------------------------------------------------------------------
const browser = await launchBrowser();
console.log(`Capturing against ${APP_URL} (API stub at ${API_URL})`);
try {
  await capture(browser);
} finally {
  await browser.close();
}

// A manifest makes the provenance of the images explicit wherever they are used.
await writeFile(
  path.join(OUTPUT_DIR, 'README.md'),
  `# Screenshots

Generated by \`tools/preview/capture.mjs\` against the React frontend with the preview harness
(\`tools/preview/server.js\`) answering the documented API contract — **not** against the Java backend.
They show the interface as it is built; the data is fixture data from \`tools/preview/fixtures.js\`.

Regenerate:

\`\`\`bash
node tools/preview/server.js         # terminal 1
cd frontend && npm run dev           # terminal 2
node tools/preview/capture.mjs       # terminal 3
\`\`\`

| File | Screen |
| --- | --- |
${shots
    .map(
      (file) =>
        `| \`${path.basename(file)}\` | ${path
          .basename(file, '.png')
          .replace(/^\d+-/, '')
          .replace(/-/g, ' ')} |`,
    )
    .join('\n')}
`,
  'utf8',
);
console.log(`\n${shots.length} screenshots written to docs/screenshots/`);
