import { Link } from 'react-router-dom';

import { useDocumentTitle } from '../hooks/useDocumentTitle.js';

export function NotFoundPage() {
  useDocumentTitle('Page not found');
  return (
    <main className="auth-page">
      <div className="auth-card">
        <div className="brand">
          <span className="brand__dot" aria-hidden="true" />
          REALCHAT
        </div>
        <h1>That page does not exist</h1>
        <p className="auth-card__subtitle">
          The link may be old or mistyped. Your conversations are still where you left them.
        </p>
        <Link className="btn btn--block" to="/">
          Back to your chats
        </Link>
      </div>
    </main>
  );
}

export default NotFoundPage;
