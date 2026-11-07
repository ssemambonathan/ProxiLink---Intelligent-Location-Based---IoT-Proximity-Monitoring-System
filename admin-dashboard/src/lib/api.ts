import { clearSession, getSession, updateTokens } from '@/lib/session';
import type { TokenPair } from '@/lib/types';

/**
 * Typed REST client for the ProxiLink API (documentation/api-contract.md).
 *
 * - URL is origin-relative by default (`/api/v1`) and goes through the Vite proxy.
 * - Access token is attached from the persisted session.
 * - On 401 a single-flight POST /auth/refresh is attempted once, then the request
 *   is retried; a failed refresh clears the session and notifies the app.
 * - Network failures surface as ApiError(network: true) so screens can render an
 *   "API unreachable" state instead of crashing.
 */

const rawBase = (import.meta.env.VITE_API_URL ?? '').trim();

function resolveBaseUrl(): string {
  if (!rawBase) return '/api/v1';
  const trimmed = rawBase.replace(/\/+$/, '');
  // Allow passing just an origin (https://api.example.com) or a full base path.
  try {
    const url = new URL(trimmed);
    if (url.pathname === '' || url.pathname === '/') return `${trimmed}/api/v1`;
  } catch {
    // Relative base such as /api/v1 — use as-is.
  }
  return trimmed;
}

export const API_BASE_URL = resolveBaseUrl();
export const API_STATUS_EVENT = 'proxilink:api-status';
export const SESSION_EXPIRED_EVENT = 'proxilink:session-expired';

export type QueryValue = string | number | boolean | null | undefined;
export type QueryParams = Record<string, QueryValue>;

export function buildQuery(params?: QueryParams): string {
  if (!params) return '';
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') return;
    search.set(key, String(value));
  });
  const qs = search.toString();
  return qs ? `?${qs}` : '';
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly violations: Record<string, string>;
  readonly path?: string;
  readonly network: boolean;

  constructor(
    status: number,
    code: string,
    message: string,
    options: { violations?: Record<string, string>; path?: string } = {},
  ) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.violations = options.violations ?? {};
    this.path = options.path;
    this.network = status === 0;
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError;
}

export function errorMessage(error: unknown): string {
  if (isApiError(error)) return error.message;
  if (error instanceof Error) return error.message;
  return 'Unexpected error';
}

/** Human-friendly copy for the most common API failure modes. */
export function describeApiError(error: ApiError): { title: string; description: string } {
  if (error.network) {
    return {
      title: 'API unreachable',
      description:
        'The dashboard could not reach the ProxiLink API. Nothing is cached or invented — this view will populate as soon as the backend responds.',
    };
  }
  switch (error.status) {
    case 401:
      return { title: 'Session expired', description: 'Please sign in again to continue.' };
    case 403:
      return { title: 'Not permitted', description: 'Your account does not have access to this resource.' };
    case 404:
      return { title: 'Not found', description: 'The requested record no longer exists.' };
    case 409:
      return { title: 'Conflict', description: error.message };
    case 422:
      return { title: 'Rejected by the server', description: error.message };
    case 429:
      return { title: 'Rate limited', description: 'Too many requests — wait a moment and retry.' };
    default:
      return {
        title: error.status >= 500 ? 'Server error' : 'Request failed',
        description: error.message,
      };
  }
}

function emitApiStatus(online: boolean): void {
  window.dispatchEvent(new CustomEvent(API_STATUS_EVENT, { detail: { online } }));
}

function emitSessionExpired(): void {
  window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));
}

async function parseErrorResponse(response: Response): Promise<ApiError> {
  let payload: Record<string, unknown> | null = null;
  try {
    const text = await response.text();
    if (text) {
      const parsed: unknown = JSON.parse(text);
      if (typeof parsed === 'object' && parsed !== null) payload = parsed as Record<string, unknown>;
    }
  } catch {
    payload = null;
  }
  const violationsRaw = payload?.violations;
  const violations: Record<string, string> = {};
  if (violationsRaw && typeof violationsRaw === 'object') {
    Object.entries(violationsRaw as Record<string, unknown>).forEach(([key, value]) => {
      if (typeof value === 'string') violations[key] = value;
    });
  }
  const fallback = response.statusText || `HTTP ${response.status}`;
  const message =
    (typeof payload?.message === 'string' && payload.message) ||
    (typeof payload?.error === 'string' && payload.error) ||
    fallback;
  return new ApiError(response.status, typeof payload?.code === 'string' ? payload.code : `HTTP_${response.status}`, message, {
    violations,
    path: typeof payload?.path === 'string' ? payload.path : undefined,
  });
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PATCH' | 'PUT' | 'DELETE';
  body?: unknown;
  query?: QueryParams;
  auth?: boolean;
  signal?: AbortSignal;
}

let refreshInFlight: Promise<string | null> | null = null;

/**
 * Single-flight refresh: concurrent 401s share one /auth/refresh call because
 * refresh tokens rotate and are single-use.
 */
export function refreshAccessToken(): Promise<string | null> {
  if (refreshInFlight) return refreshInFlight;
  refreshInFlight = (async (): Promise<string | null> => {
    const session = getSession();
    if (!session?.refreshToken) return null;
    try {
      const pair = await send<TokenPair>(
        '/auth/refresh',
        { method: 'POST', body: { refreshToken: session.refreshToken }, auth: false },
        null,
        false,
      );
      updateTokens(pair);
      return pair.accessToken;
    } catch (error) {
      if (isApiError(error) && error.status === 401) {
        clearSession();
        emitSessionExpired();
      }
      // Network errors keep the session so a reconnect can retry later.
      return null;
    }
  })().finally(() => {
    refreshInFlight = null;
  });
  return refreshInFlight;
}

async function send<T>(
  path: string,
  options: RequestOptions,
  token: string | null,
  allowRefresh: boolean,
): Promise<T> {
  const { method = 'GET', body, query, signal } = options;
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (token) headers.Authorization = `Bearer ${token}`;

  // A hung proxy or black-holed network must not leave a view on skeletons forever.
  const timeoutSignal = AbortSignal.timeout(15_000);
  const requestSignal = signal ? AbortSignal.any([signal, timeoutSignal]) : timeoutSignal;

  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}${buildQuery(query)}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: requestSignal,
    });
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError' && !timeoutSignal.aborted) throw error;
    emitApiStatus(false);
    if (timeoutSignal.aborted && !(signal?.aborted ?? false)) {
      throw new ApiError(0, 'TIMEOUT', 'The ProxiLink API did not respond in time.', { path });
    }
    throw new ApiError(0, 'NETWORK_ERROR', 'The ProxiLink API could not be reached.', { path });
  }

  emitApiStatus(true);

  if (response.status === 401 && allowRefresh && options.auth !== false) {
    const fresh = await refreshAccessToken();
    if (fresh) {
      return send<T>(path, options, fresh, false);
    }
    if (!getSession()) emitSessionExpired();
  }

  if (!response.ok) {
    const apiError = await parseErrorResponse(response);
    if (response.status === 401 && options.auth !== false) {
      clearSession();
      emitSessionExpired();
    }
    throw apiError;
  }

  if (response.status === 204) return undefined as T;
  const text = await response.text();
  if (!text) return undefined as T;
  try {
    return JSON.parse(text) as T;
  } catch {
    throw new ApiError(response.status, 'INVALID_JSON', 'The API returned an unreadable response.', { path });
  }
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const token = options.auth === false ? null : getSession()?.accessToken ?? null;
  return send<T>(path, options, token, options.auth !== false);
}

/** Lightweight reachability probe used by the "retry" button on offline banners. */
export async function probeApi(): Promise<boolean> {
  try {
    const response = await fetch(`${API_BASE_URL}/config/public`, {
      method: 'GET',
      headers: { Accept: 'application/json' },
    });
    const online = response.status > 0;
    emitApiStatus(online);
    return online;
  } catch {
    emitApiStatus(false);
    return false;
  }
}
