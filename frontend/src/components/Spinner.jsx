export function Spinner({ large = false, label = 'Loading' }) {
  return (
    <span className={`spinner${large ? ' spinner--large' : ''}`} role="progressbar" aria-label={label} />
  );
}

export function LoadingBlock({ label = 'Loading…' }) {
  return (
    <div className="loading-block" role="status" aria-live="polite">
      {/* The progressbar carries the specific message, not a generic "Loading": that is what a
          screen-reader user hears, and "Checking your session…" is far more useful than "Loading". */}
      <Spinner label={label} />
      <span aria-hidden="true">{label}</span>
    </div>
  );
}

export function SkeletonRows({ rows = 5 }) {
  return (
    <div className="stack" aria-hidden="true">
      {Array.from({ length: rows }).map((_, index) => (
        <div key={index} className="skeleton" style={{ height: 44 }} />
      ))}
    </div>
  );
}

export default Spinner;
