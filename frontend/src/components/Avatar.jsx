import { useState } from 'react';

/**
 * Pseudonymous avatar.
 *
 * A deterministic colour plus initials keeps conversations readable without requiring users to upload
 * anything — the product is deliberately usable with no photo at all, and never falls back to a
 * generic "anonymous" silhouette that makes every peer look identical.
 */
const PALETTE = ['#1f6feb', '#7c3aed', '#0f766e', '#b45309', '#be185d', '#0369a1', '#4d7c0f', '#9333ea'];

function initials(displayName, username) {
  const source = (displayName || username || '?').trim();
  const parts = source.split(/\s+/).filter(Boolean);
  if (parts.length === 0) return '?';
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

function paletteFor(seed) {
  if (!seed) return PALETTE[0];
  let hash = 0;
  for (let index = 0; index < seed.length; index += 1) {
    hash = (hash * 31 + seed.charCodeAt(index)) % 100000;
  }
  return PALETTE[hash % PALETTE.length];
}

export function Avatar({ user, size = 40, presence, showPresence = false }) {
  const [broken, setBroken] = useState(false);
  const label = user?.displayName || user?.username || 'Unknown user';
  const url = user?.avatarUrl;

  return (
    <span
      className="avatar"
      style={{
        width: size,
        height: size,
        fontSize: Math.max(11, size * 0.36),
        background: url && !broken ? undefined : paletteFor(user?.id || user?.username),
        color: '#fff',
      }}
      aria-hidden="true"
    >
      {url && !broken ? (
        <img src={url} alt="" onError={() => setBroken(true)} />
      ) : (
        initials(user?.displayName, user?.username)
      )}
      {showPresence ? (
        <span
          className={`avatar__presence${
            presence === 'ONLINE' ? ' avatar__presence--online' : ''
          }${presence === 'AWAY' ? ' avatar__presence--away' : ''}`}
        />
      ) : null}
      <span className="sr-only">{label}</span>
    </span>
  );
}

export default Avatar;
