import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { ApiError, SESSION_EXPIRED_EVENT, apiRequest, isApiError } from '@/lib/api';
import { decodeJwt, rolesFromClaims, tokenExpiry } from '@/lib/jwt';
import {
  clearSession,
  getSession,
  saveAuthResponse,
  saveUser,
} from '@/lib/session';
import type { AuthResponse, User } from '@/lib/types';
import { useToast } from '@/context/ToastContext';

type AuthStatus = 'booting' | 'authenticated' | 'unauthenticated';

interface AuthState {
  status: AuthStatus;
  user: User | null;
  accessToken: string | null;
  sessionExpiresAt: number | null;
}

interface AuthValue extends AuthState {
  isAdmin: boolean;
  roles: string[];
  login: (email: string, password: string) => Promise<User>;
  logout: () => Promise<void>;
  refreshUser: () => Promise<void>;
}

const AuthContext = createContext<AuthValue | null>(null);

const initialState: AuthState = {
  status: 'booting',
  user: null,
  accessToken: null,
  sessionExpiresAt: null,
};

const signedOut: AuthState = {
  status: 'unauthenticated',
  user: null,
  accessToken: null,
  sessionExpiresAt: null,
};

/** The organization claim is used for display only; authorization is server-side. */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>(initialState);
  const toast = useToast();

  const applyUser = useCallback((user: User) => {
    saveUser(user);
    setState((current) => ({ ...current, user }));
  }, []);

  const hydrateSession = useCallback(
    async (hadCachedUser: boolean) => {
      try {
        const me = await apiRequest<User>('/auth/me');
        applyUser(me);
        const session = getSession();
        if (session) {
          setState((current) => ({
            ...current,
            accessToken: session.accessToken,
            sessionExpiresAt: tokenExpiry(session.accessToken) ?? session.expiresAt,
          }));
        }
      } catch (error) {
        if (isApiError(error) && error.status === 401) {
          clearSession();
          setState(signedOut);
          return;
        }
        if (error instanceof ApiError && error.network && !hadCachedUser) {
          // No profile snapshot to render offline — fall back to the login screen.
          setState(signedOut);
        }
        // With a cached profile we keep the shell and surface "API unreachable".
      }
    },
    [applyUser],
  );

  useEffect(() => {
    const session = getSession();
    if (!session) {
      setState(signedOut);
      return;
    }
    const expiry = tokenExpiry(session.accessToken) ?? session.expiresAt;
    setState({
      status: 'authenticated',
      user: session.user,
      accessToken: session.accessToken,
      sessionExpiresAt: expiry,
    });
    void hydrateSession(Boolean(session.user));
    // Runs once on mount; token rotation is handled inside the API client.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    const handler = () => {
      setState(signedOut);
      toast.info('Session ended', 'Your session expired. Please sign in again.');
    };
    window.addEventListener(SESSION_EXPIRED_EVENT, handler);
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, handler);
  }, [toast]);

  // Keep the expiry display honest across silent refreshes.
  useEffect(() => {
    if (state.status !== 'authenticated') return;
    const timer = window.setInterval(() => {
      const session = getSession();
      if (!session) return;
      const next = tokenExpiry(session.accessToken) ?? session.expiresAt;
      setState((current) =>
        current.accessToken === session.accessToken && current.sessionExpiresAt === next
          ? current
          : { ...current, accessToken: session.accessToken, sessionExpiresAt: next },
      );
    }, 60_000);
    return () => window.clearInterval(timer);
  }, [state.status]);

  const login = useCallback(async (email: string, password: string) => {
    const response = await apiRequest<AuthResponse>('/auth/login', {
      method: 'POST',
      auth: false,
      body: { email: email.trim(), password },
    });
    saveAuthResponse(response);
    setState({
      status: 'authenticated',
      user: response.user,
      accessToken: response.accessToken,
      sessionExpiresAt: tokenExpiry(response.accessToken) ?? Date.now() + response.expiresInSeconds * 1000,
    });
    return response.user;
  }, []);

  const logout = useCallback(async () => {
    const session = getSession();
    if (session) {
      try {
        await apiRequest<void>('/auth/logout', {
          method: 'POST',
          body: { refreshToken: session.refreshToken },
        });
      } catch {
        // Logout must always succeed locally, even when the API is unreachable.
      }
    }
    clearSession();
    setState(signedOut);
  }, []);

  const refreshUser = useCallback(async () => {
    const me = await apiRequest<User>('/auth/me');
    applyUser(me);
  }, [applyUser]);

  const { isAdmin, roles } = useMemo(() => {
    const claims = decodeJwt(state.accessToken);
    const tokenRoles = rolesFromClaims(claims);
    const fallback = Array.isArray(state.user?.roles) ? state.user.roles.map((role) => String(role).toUpperCase()) : [];
    const effective = tokenRoles.length > 0 ? tokenRoles : fallback;
    return { isAdmin: effective.includes('ADMIN'), roles: effective };
  }, [state.accessToken, state.user]);

  const value = useMemo<AuthValue>(
    () => ({ ...state, isAdmin, roles, login, logout, refreshUser }),
    [state, isAdmin, roles, login, logout, refreshUser],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used within an AuthProvider');
  return context;
}
