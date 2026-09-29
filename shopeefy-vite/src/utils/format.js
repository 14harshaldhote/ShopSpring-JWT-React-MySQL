const rupees = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 });
const dateTime = new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium', timeStyle: 'short' });
const dateOnly = new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium' });
const relative = new Intl.RelativeTimeFormat('en', { numeric: 'auto' });

export const formatPrice = (value) => rupees.format(value ?? 0);

export function formatDateTime(value) {
  return value ? dateTime.format(new Date(value)) : '—';
}

export function formatDate(value) {
  return value ? dateOnly.format(new Date(value)) : '—';
}

const UNITS = [
  ['year', 365 * 24 * 3600],
  ['month', 30 * 24 * 3600],
  ['day', 24 * 3600],
  ['hour', 3600],
  ['minute', 60],
];

export function timeAgo(value) {
  if (!value) return '—';
  const seconds = Math.round((new Date(value).getTime() - Date.now()) / 1000);
  for (const [unit, size] of UNITS) {
    if (Math.abs(seconds) >= size) return relative.format(Math.round(seconds / size), unit);
  }
  return 'just now';
}

/** LOGIN_SUCCESS -> "Login success" */
export function humanize(value) {
  if (!value) return '';
  const text = String(value).replaceAll('_', ' ').toLowerCase();
  return text.charAt(0).toUpperCase() + text.slice(1);
}

export function fullName(person) {
  return [person?.firstName, person?.lastName].filter(Boolean).join(' ');
}
