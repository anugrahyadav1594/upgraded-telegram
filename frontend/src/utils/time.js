/**
 * Time formatting helpers.
 *
 * All formatting goes through `Intl` with an explicit locale rather than hand-rolled concatenation:
 * date order, 12/24-hour clocks and relative-time wording differ per locale, and getting that wrong is
 * a visible bug in a product that ships to more than one country.
 */
const RELATIVE = new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' });
const TIME = new Intl.DateTimeFormat(undefined, { hour: '2-digit', minute: '2-digit' });
const DAY = new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric' });
const FULL = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' });

const DIVISIONS = [
  { amount: 60, unit: 'seconds' },
  { amount: 60, unit: 'minutes' },
  { amount: 24, unit: 'hours' },
  { amount: 7, unit: 'days' },
  { amount: 4.34524, unit: 'weeks' },
  { amount: 12, unit: 'months' },
  { amount: Number.POSITIVE_INFINITY, unit: 'years' },
];

/** "3 minutes ago", "yesterday" — for list previews. */
export function relativeTime(value) {
  if (!value) return '';
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  let duration = (date.getTime() - Date.now()) / 1000;
  for (const division of DIVISIONS) {
    if (Math.abs(duration) < division.amount) {
      return RELATIVE.format(Math.round(duration), division.unit);
    }
    duration /= division.amount;
  }
  return '';
}

export function clockTime(value) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '' : TIME.format(date);
}

/** Day separators in a chat: "Today", "Yesterday", "12 Mar" or a full date for other years. */
export function dayLabel(value) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  const today = new Date();
  const yesterday = new Date(today);
  yesterday.setDate(today.getDate() - 1);
  const sameDay = (a, b) => a.toDateString() === b.toDateString();
  if (sameDay(date, today)) return 'Today';
  if (sameDay(date, yesterday)) return 'Yesterday';
  return date.getFullYear() === today.getFullYear() ? DAY.format(date) : FULL.format(date);
}

export function fullTimestamp(value) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '' : FULL.format(date);
}

/** ISO string for a datetime attribute (screen readers and tooltips benefit from the exact value). */
export function isoTime(value) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '' : date.toISOString();
}
