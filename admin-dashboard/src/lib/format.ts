import { format, formatDistanceToNowStrict, isValid, parseISO } from 'date-fns';

function toDate(value: string | number | Date | null | undefined): Date | null {
  if (value === null || value === undefined || value === '') return null;
  const date = value instanceof Date ? value : typeof value === 'number' ? new Date(value) : parseISO(value);
  return isValid(date) ? date : null;
}

export function formatDateTime(value: string | number | Date | null | undefined): string {
  const date = toDate(value);
  return date ? format(date, 'd MMM yyyy, HH:mm:ss') : '—';
}

export function formatDate(value: string | number | Date | null | undefined): string {
  const date = toDate(value);
  return date ? format(date, 'd MMM yyyy') : '—';
}

export function formatTime(value: string | number | Date | null | undefined): string {
  const date = toDate(value);
  return date ? format(date, 'HH:mm:ss') : '—';
}

export function formatDateTimeShort(value: string | number | Date | null | undefined): string {
  const date = toDate(value);
  return date ? format(date, 'd MMM, HH:mm') : '—';
}

export function relativeTime(value: string | number | Date | null | undefined): string {
  const date = toDate(value);
  if (!date) return 'never';
  const diff = Date.now() - date.getTime();
  if (Math.abs(diff) < 15_000) return 'just now';
  return formatDistanceToNowStrict(date, { addSuffix: true });
}

export function formatNumber(value: number | null | undefined, options?: Intl.NumberFormatOptions): string {
  if (typeof value !== 'number' || Number.isNaN(value)) return '—';
  return new Intl.NumberFormat(undefined, options).format(value);
}

export function formatCompact(value: number | null | undefined): string {
  if (typeof value !== 'number' || Number.isNaN(value)) return '—';
  return new Intl.NumberFormat(undefined, { notation: 'compact', maximumFractionDigits: 1 }).format(value);
}

export function formatDbm(value: number | null | undefined): string {
  if (typeof value !== 'number' || Number.isNaN(value)) return '—';
  return `${Math.round(value)} dBm`;
}

export function formatMeters(value: number | null | undefined): string {
  if (typeof value !== 'number' || Number.isNaN(value)) return '—';
  if (value < 1000) return `${value.toFixed(value < 10 ? 1 : 0)} m`;
  return `${(value / 1000).toFixed(1)} km`;
}

export function formatCoords(latitude: number | null | undefined, longitude: number | null | undefined): string {
  if (typeof latitude !== 'number' || typeof longitude !== 'number') return '—';
  if (Number.isNaN(latitude) || Number.isNaN(longitude)) return '—';
  return `${latitude.toFixed(4)}, ${longitude.toFixed(4)}`;
}

export function formatUptime(seconds: number | null | undefined): string {
  if (typeof seconds !== 'number' || seconds < 0) return '—';
  const days = Math.floor(seconds / 86_400);
  const hours = Math.floor((seconds % 86_400) / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  if (days > 0) return `${days}d ${hours}h`;
  if (hours > 0) return `${hours}h ${minutes}m`;
  return `${minutes}m`;
}

export function titleCase(value: string | null | undefined): string {
  if (!value) return '—';
  return value
    .toLowerCase()
    .split(/[_\s-]+/)
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ');
}

export function initials(name: string | null | undefined, fallback = '?'): string {
  if (!name) return fallback;
  const parts = name.trim().split(/\s+/).slice(0, 2);
  return parts.map((part) => part.charAt(0).toUpperCase()).join('') || fallback;
}

/** ISO date (yyyy-MM-dd), `offset` days from today — used for analytics ranges. */
export function isoDateOffset(days: number): string {
  const date = new Date();
  date.setDate(date.getDate() + days);
  return format(date, 'yyyy-MM-dd');
}

/** Local `datetime-local` value (yyyy-MM-ddTHH:mm) to an ISO-8601 UTC string. */
export function localInputToIso(value: string): string | undefined {
  if (!value) return undefined;
  const date = new Date(value);
  return isValid(date) ? date.toISOString() : undefined;
}
