/**
 * Inline icon set.
 *
 * Hand-written SVGs instead of an icon package: the app needs about fifteen glyphs, and a dependency
 * would ship hundreds. Every icon is `aria-hidden` — the accessible name belongs to the button around
 * it, which is what a screen reader should read ("Send message", not "icon-arrow-up").
 */
const PATHS = {
  chat: 'M21 12a8 8 0 0 1-8 8H8l-5 3 1.4-4.2A8 8 0 0 1 13 4a8 8 0 0 1 8 8Z',
  search: 'M11 19a8 8 0 1 1 0-16 8 8 0 0 1 0 16Zm0 0 5 5',
  send: 'M4 12 20 4l-4 16-4-6-8-2Z',
  back: 'M15 5l-7 7 7 7',
  close: 'M6 6l12 12M18 6 6 18',
  plus: 'M12 5v14M5 12h14',
  bell: 'M18 16V11a6 6 0 1 0-12 0v5l-2 3h16l-2-3ZM10 22h4',
  user: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm8 8a8 8 0 1 0-16 0',
  settings:
    'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6Zm8.4-3a8.4 8.4 0 0 0-.1-1.2l2-1.6-2-3.4-2.4 1a8.5 8.5 0 0 0-2-1.2L15.5 3h-4l-.4 2.6a8.5 8.5 0 0 0-2 1.2l-2.4-1-2 3.4 2 1.6a8.4 8.4 0 0 0 0 2.4l-2 1.6 2 3.4 2.4-1a8.5 8.5 0 0 0 2 1.2l.4 2.6h4l.4-2.6a8.5 8.5 0 0 0 2-1.2l2.4 1 2-3.4-2-1.6c.1-.4.1-.8.1-1.2Z',
  logout: 'M15 12H3m0 0 4-4m-4 4 4 4M11 5h8v14h-8',
  block: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18ZM5.6 5.6l12.8 12.8',
  flag: 'M5 21V4m0 0h11l-1.5 4L16 12H5',
  check: 'M4 12.5 9 17.5 20 6.5',
  checkDouble: 'M2 12.5 6.5 17 15 7M9 12.5 13.5 17 22 7',
  clock: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18Zm0-13v5l3 2',
  alert: 'M12 9v4m0 4h.01M10.3 3.9 2.4 17.6A2 2 0 0 0 4.1 21h15.8a2 2 0 0 0 1.7-3.4L13.7 3.9a2 2 0 0 0-3.4 0Z',
  refresh: 'M20 12a8 8 0 1 1-2.3-5.7M20 4v4h-4',
  chevronDown: 'M6 9l6 6 6-6',
  shield: 'M12 3l8 3v6c0 4.4-3.4 8.4-8 10-4.6-1.6-8-5.6-8-10V6l8-3Z',
  sun: 'M12 17a5 5 0 1 0 0-10 5 5 0 0 0 0 10Zm0-14v2m0 14v2M3 12h2m14 0h2M5.6 5.6l1.4 1.4m10 10 1.4 1.4m0-12.8-1.4 1.4m-10 10L5.6 18.4',
  moon: 'M20 14.5A8.5 8.5 0 0 1 9.5 4a8.5 8.5 0 1 0 10.5 10.5Z',
  monitor: 'M4 5h16v10H4zM9 19h6m-3-4v4',
};

export function Icon({ name, size = 20, className = '', title }) {
  const path = PATHS[name];
  if (!path) return null;
  return (
    <svg
      className={className}
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.7"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
      role="img"
    >
      {title ? <title>{title}</title> : null}
      <path d={path} />
    </svg>
  );
}

export default Icon;
