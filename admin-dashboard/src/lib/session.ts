import type { AuthResponse, TokenPair, User } from '@/lib/types';

/**
 * Session persistence.
 *
 * Tokens live in localStorage so a reload keeps the operator signed in. This is
 * acceptable for this demonstration build but it is XSS-readable — see the
 * README for the tradeoff and the recommended httpOnly-cookie alternative.
 * Only tokens and a small profile snapshot are stored; no device or event data.
 */
const STORAGE_KEY = 'proxilink.session.v1';

export interface StoredSession {
  accessToken: string;
  refreshToken: string;
  expiresAt: number;
  user: User | null;
}

let cached: StoredSession | null | undefined;

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null;

function readStorage(): StoredSession | null {
  if (cached !== undefined) return cached;
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      cached = null;
      return null;
    }
    const parsed: unknown = JSON.parse(raw);
    if (!isRecord(parsed) || typeof parsed.accessToken !== 'string' || typeof parsed.refreshToken !== 'string') {
      cached = null;
      return null;
    }
    cached = {
      accessToken: parsed.accessToken,
      refreshToken: parsed.refreshToken,
      expiresAt: typeof parsed.expiresAt === 'number' ? parsed.expiresAt : 0,
      user: isRecord(parsed.user) ? (parsed.user as unknown as User) : null,
    };
    return cached;
  } catch {
    cached = null;
    return null;
  }
}

function persist(session: StoredSession | null): void {
  cached = session;
  try {
    if (session) window.localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    else window.localStorage.removeItem(STORAGE_KEY);
  } catch {
    // Storage can be unavailable (private mode/quota); the in-memory cache still works.
  }
}

export function getSession(): StoredSession | null {
  return readStorage();
}

export function getAccessToken(): string | null {
  return readStorage()?.accessToken ?? null;
}

export function saveAuthResponse(pair: AuthResponse): StoredSession {
  const session: StoredSession = {
    accessToken: pair.accessToken,
    refreshToken: pair.refreshToken,
    expiresAt: Date.now() + Math.max(1, pair.expiresInSeconds) * 1000,
    user: pair.user ?? null,
  };
  persist(session);
  return session;
}

export function updateTokens(pair: TokenPair): StoredSession | null {
  const current = readStorage();
  if (!current) return null;
  const next: StoredSession = {
    ...current,
    accessToken: pair.accessToken,
    refreshToken: pair.refreshToken,
    expiresAt: Date.now() + Math.max(1, pair.expiresInSeconds) * 1000,
  };
  persist(next);
  return next;
}

export function saveUser(user: User): StoredSession | null {
  const current = readStorage();
  if (!current) return null;
  const next: StoredSession = { ...current, user };
  persist(next);
  return next;
}

export function clearSession(): void {
  persist(null);
}
