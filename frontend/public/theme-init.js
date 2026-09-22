/**
 * Theme bootstrap — loaded synchronously from <head>, before the app bundle.
 *
 * It lives in its own file rather than inline so the Content-Security-Policy can stay
 * `script-src 'self'` with no hash exception and no 'unsafe-inline': an inline script would be one
 * more thing to keep in sync between the HTML and the nginx configuration, and a drifting policy
 * silently blocks the theme on first paint.
 *
 * Only the non-sensitive theme preference is read here. No token, no session, no user data.
 */
(function () {
  try {
    var stored = localStorage.getItem('realchat.theme') || 'system';
    var prefersDark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
    var dark = stored === 'dark' || (stored === 'system' && prefersDark);
    document.documentElement.dataset.theme = dark ? 'dark' : 'light';
    document.documentElement.dataset.themePreference = stored;
  } catch (error) {
    // Private browsing without storage, or a locked-down client: fall back to light and carry on.
    document.documentElement.dataset.theme = 'light';
  }
})();
