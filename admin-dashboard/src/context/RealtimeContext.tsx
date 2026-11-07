import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import { useAuth } from '@/context/AuthContext';
import { getSession } from '@/lib/session';
import type {
  AppNotification,
  DeviceStatusFrame,
  LocationEvent,
  ProximityEvent,
  WsFrame,
} from '@/lib/types';
import { RealtimeClient, type ConnectionState } from '@/lib/ws';

interface RealtimeValue {
  connection: ConnectionState;
  attempt: number;
  lastFrameAt: number | null;
  serverTime: string | null;
  paused: boolean;
  setPaused: (paused: boolean) => void;
  reconnect: () => void;
  proximityEvents: ProximityEvent[];
  locationEvents: LocationEvent[];
  deviceStatuses: Record<string, DeviceStatusFrame>;
  lastNotification: AppNotification | null;
}

const RealtimeContext = createContext<RealtimeValue | null>(null);

const MAX_FEED = 80;

/**
 * Owns the single WebSocket connection. Frames are fanned out into small capped
 * buffers that pages merge with their REST snapshots.
 */
export function RealtimeProvider({ children }: { children: ReactNode }) {
  const { status, accessToken, user } = useAuth();
  const [connection, setConnection] = useState<ConnectionState>('idle');
  const [attempt, setAttempt] = useState(0);
  const [lastFrameAt, setLastFrameAt] = useState<number | null>(null);
  const [serverTime, setServerTime] = useState<string | null>(null);
  const [paused, setPausedState] = useState(false);
  const [proximityEvents, setProximityEvents] = useState<ProximityEvent[]>([]);
  const [locationEvents, setLocationEvents] = useState<LocationEvent[]>([]);
  const [deviceStatuses, setDeviceStatuses] = useState<Record<string, DeviceStatusFrame>>({});
  const [lastNotification, setLastNotification] = useState<AppNotification | null>(null);

  const clientRef = useRef<RealtimeClient | null>(null);
  const pausedRef = useRef(paused);
  useEffect(() => {
    pausedRef.current = paused;
  }, [paused]);

  const handleState = useCallback((state: ConnectionState, meta: { attempt: number; lastFrameAt: number | null }) => {
    setConnection(state);
    setAttempt(meta.attempt);
    setLastFrameAt(meta.lastFrameAt);
  }, []);

  const handleFrame = useCallback((frame: WsFrame) => {
    switch (frame.type) {
      case 'hello':
        setServerTime(frame.payload.serverTime);
        break;
      case 'proximity.event':
        if (pausedRef.current) break;
        setProximityEvents((current) => [frame.payload, ...current].slice(0, MAX_FEED));
        break;
      case 'location.event':
        if (pausedRef.current) break;
        setLocationEvents((current) => [frame.payload, ...current].slice(0, MAX_FEED));
        break;
      case 'device.status':
        setDeviceStatuses((current) => ({
          ...current,
          [frame.payload.deviceId]: {
            deviceId: frame.payload.deviceId,
            status: frame.payload.status,
            lastSeenAt: frame.payload.lastSeenAt ?? null,
            lastRssi: frame.payload.lastRssi ?? null,
            receivedAt: Date.now(),
          },
        }));
        break;
      case 'notification':
        setLastNotification(frame.payload);
        break;
      case 'pong':
        break;
      default:
        break;
    }
  }, []);

  useEffect(() => {
    if (status !== 'authenticated' || !accessToken) {
      setConnection('idle');
      setAttempt(0);
      setLastFrameAt(null);
      return;
    }
    const client = new RealtimeClient({
      getToken: () => getSession()?.accessToken ?? null,
      onFrame: handleFrame,
      onState: handleState,
    });
    clientRef.current = client;
    client.connect();
    return () => {
      client.disconnect();
      if (clientRef.current === client) clientRef.current = null;
    };
  }, [status, accessToken, handleFrame, handleState]);

  // Never carry one operator's live buffers into another session.
  const identity = status === 'authenticated' ? user?.id ?? 'unknown' : null;
  useEffect(() => {
    setProximityEvents([]);
    setLocationEvents([]);
    setDeviceStatuses({});
    setLastNotification(null);
  }, [identity]);

  const setPaused = useCallback((value: boolean) => setPausedState(value), []);
  const reconnect = useCallback(() => clientRef.current?.reconnectNow(), []);

  const value = useMemo<RealtimeValue>(
    () => ({
      connection,
      attempt,
      lastFrameAt,
      serverTime,
      paused,
      setPaused,
      reconnect,
      proximityEvents,
      locationEvents,
      deviceStatuses,
      lastNotification,
    }),
    [
      connection,
      attempt,
      lastFrameAt,
      serverTime,
      paused,
      setPaused,
      reconnect,
      proximityEvents,
      locationEvents,
      deviceStatuses,
      lastNotification,
    ],
  );

  return <RealtimeContext.Provider value={value}>{children}</RealtimeContext.Provider>;
}

export function useRealtime(): RealtimeValue {
  const context = useContext(RealtimeContext);
  if (!context) throw new Error('useRealtime must be used within a RealtimeProvider');
  return context;
}
