import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { API_STATUS_EVENT, probeApi } from '@/lib/api';

interface ConnectivityValue {
  /** False once a request has failed at the network layer. */
  online: boolean;
  checking: boolean;
  recheck: () => Promise<void>;
}

const ConnectivityContext = createContext<ConnectivityValue | null>(null);

/**
 * Tracks whether the REST API is reachable so the UI can show a single, calm
 * banner instead of duplicating "connection failed" in every panel.
 */
export function ConnectivityProvider({ children }: { children: ReactNode }) {
  const [online, setOnline] = useState(true);
  const [checking, setChecking] = useState(false);

  useEffect(() => {
    const handler = (event: Event) => {
      const detail = (event as CustomEvent<{ online?: boolean }>).detail;
      if (typeof detail?.online === 'boolean') setOnline(detail.online);
    };
    window.addEventListener(API_STATUS_EVENT, handler);
    return () => window.removeEventListener(API_STATUS_EVENT, handler);
  }, []);

  const recheck = useCallback(async () => {
    setChecking(true);
    try {
      await probeApi();
    } finally {
      setChecking(false);
    }
  }, []);

  const value = useMemo<ConnectivityValue>(() => ({ online, checking, recheck }), [online, checking, recheck]);

  return <ConnectivityContext.Provider value={value}>{children}</ConnectivityContext.Provider>;
}

export function useConnectivity(): ConnectivityValue {
  const context = useContext(ConnectivityContext);
  if (!context) throw new Error('useConnectivity must be used within a ConnectivityProvider');
  return context;
}
