export function Spinner({ large = false, label = 'Loading' }) {
  return (
    <span className={`spinner${large ? ' spinner--large' : ''}`} role="progressbar" aria-label={label} />
  );
}

export function LoadingBlock({ label = 'Loading…' }) {
  return (
    <div className="loading-block">
      <Spinner />
      <span>{label}</span>
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
