import { Component } from 'react';

import { Icon } from './Icon.jsx';

/**
 * Last line of defence.
 *
 * A render error in one pane must not leave a blank white page: the boundary shows what happened, keeps
 * the rest of the shell usable where possible, and offers a reload. Errors are reported to the console
 * with the component stack so they are diagnosable rather than silently swallowed.
 */
export class ErrorBoundary extends Component {
  constructor(props) {
    super(props);
    this.state = { error: null };
  }

  static getDerivedStateFromError(error) {
    return { error };
  }

  componentDidCatch(error, info) {
    console.error('Unhandled UI error', error, info?.componentStack);
  }

  render() {
    if (!this.state.error) return this.props.children;

    return (
      <div className="page">
        <div className="card">
          <div className="row">
            <Icon name="alert" size={22} />
            <h1 className="page__title">Something broke in this view</h1>
          </div>
          <p className="muted">
            The rest of the app is still running. Reloading usually clears it; if it keeps happening, the
            details below help whoever is debugging.
          </p>
          <pre className="text-xs muted" style={{ whiteSpace: 'pre-wrap' }}>
            {String(this.state.error?.message || this.state.error)}
          </pre>
          <button type="button" className="btn" onClick={() => window.location.reload()}>
            Reload the app
          </button>
        </div>
      </div>
    );
  }
}

export default ErrorBoundary;
