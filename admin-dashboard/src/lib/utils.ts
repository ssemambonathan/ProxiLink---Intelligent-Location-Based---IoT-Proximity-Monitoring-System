import clsx from 'clsx';
import type { ClassValue } from 'clsx';
import type { QueryParams } from '@/lib/api';

export function cn(...inputs: ClassValue[]): string {
  return clsx(inputs);
}

/** Build an API path with a query string, omitting empty filter values. */
export function withQuery(path: string, params: QueryParams): string {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') return;
    search.set(key, String(value));
  });
  const qs = search.toString();
  return qs ? `${path}?${qs}` : path;
}

export function saveLocalPreference(key: string, value: string): void {
  try {
    window.localStorage.setItem(`proxilink.${key}`, value);
  } catch {
    // Preferences are non-critical.
  }
}

export function readLocalPreference(key: string): string | null {
  try {
    return window.localStorage.getItem(`proxilink.${key}`);
  } catch {
    return null;
  }
}

/** Page window with ellipses, e.g. [1, '…', 4, 5, 6, '…', 12]. */
export function paginationWindow(page: number, totalPages: number, span = 1): Array<number | 'gap'> {
  if (totalPages <= 0) return [];
  const current = Math.min(Math.max(page, 0), totalPages - 1);
  const pages = new Set<number>([0, totalPages - 1]);
  for (let i = current - span; i <= current + span; i += 1) {
    if (i > 0 && i < totalPages - 1) pages.add(i);
  }
  const sorted = [...pages].sort((a, b) => a - b);
  const result: Array<number | 'gap'> = [];
  sorted.forEach((value, index) => {
    if (index > 0 && value - sorted[index - 1] > 1) result.push('gap');
    result.push(value);
  });
  return result;
}
