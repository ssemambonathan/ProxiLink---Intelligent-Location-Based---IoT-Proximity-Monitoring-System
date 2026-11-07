/**
 * Minimal JWT payload reader — used only for navigation gating and expiry
 * display. The backend remains the authority on authorization; every request
 * is scoped and validated server-side.
 */
export interface JwtClaims {
  sub?: string;
  email?: string;
  uid?: string;
  org?: string;
  organization?: string;
  roles?: unknown;
  authorities?: unknown;
  exp?: number;
  iat?: number;
}

function base64UrlDecode(segment: string): string {
  const normalised = segment.replace(/-/g, '+').replace(/_/g, '/');
  const padded = normalised.padEnd(normalised.length + ((4 - (normalised.length % 4)) % 4), '=');
  const binary = atob(padded);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) bytes[i] = binary.charCodeAt(i);
  return new TextDecoder().decode(bytes);
}

export function decodeJwt(token: string | null | undefined): JwtClaims | null {
  if (!token) return null;
  const parts = token.split('.');
  if (parts.length < 2) return null;
  try {
    const parsed: unknown = JSON.parse(base64UrlDecode(parts[1]));
    return typeof parsed === 'object' && parsed !== null ? (parsed as JwtClaims) : null;
  } catch {
    return null;
  }
}

export function rolesFromClaims(claims: JwtClaims | null): string[] {
  if (!claims) return [];
  const raw = claims.roles ?? claims.authorities;
  const values = Array.isArray(raw) ? raw : typeof raw === 'string' ? [raw] : [];
  return values
    .filter((value): value is string => typeof value === 'string')
    .map((value) => value.replace(/^ROLE_/, '').toUpperCase());
}

export function tokenExpiry(token: string | null | undefined): number | null {
  const exp = decodeJwt(token)?.exp;
  return typeof exp === 'number' ? exp * 1000 : null;
}
