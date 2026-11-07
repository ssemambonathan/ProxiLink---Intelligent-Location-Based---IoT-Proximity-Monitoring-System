import { refreshAccessToken } from '@/lib/api';
import { decodeJwt } from '@/lib/jwt';
import type { WsFrame } from '@/lib/types';

export type ConnectionState = 'idle' | 'connecting' | 'open' | 'reconnecting' | 'closed';

export interface RealtimeMeta {
  attempt: number;
  lastFrameAt: number | null;
}

interface ClientHandlers {
  getToken: () => string | null;
  onFrame: (frame: WsFrame) => void;
  onState: (state: ConnectionState, meta: RealtimeMeta) => void;
}

const HEARTBEAT_MS = 25_000;
const STALE_SOCKET_MS = 70_000;
const MAX_BACKOFF_MS = 30_000;

/**
 * WebSocket URL resolution.
 * - VITE_WS_URL wins when provided (must be the `/ws` endpoint).
 * - Otherwise derive ws(s)://<window.location.host>/ws so the Vite proxy (and
 *   any same-origin nginx deployment) works without configuration.
 * The access token travels as a query parameter because the browser WebSocket
 * API cannot set an Authorization header. Never log or display this URL.
 */
export function resolveWebSocketUrl(token: string): string {
  const override = (import.meta.env.VITE_WS_URL ?? '').trim();
  const base =
    override ||
    `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}/ws`;
  const url = new URL(base);
  url.searchParams.set('token', token);
  return url.toString();
}

/**
 * Resilient JSON frame client:
 * - exponential backoff with jitter, capped at 30s
 * - proactive token refresh on later attempts and when the JWT is near expiry
 * - 25s heartbeat ping and stale-socket detection
 * - silent (token-free) diagnostics.
 */
export class RealtimeClient {
  private socket: WebSocket | null = null;
  private token: string | null = null;
  private attempt = 0;
  private manualClose = true;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private heartbeatTimer: ReturnType<typeof setInterval> | null = null;
  private lastFrameAt: number | null = null;
  private state: ConnectionState = 'idle';
  private disposed = false;

  constructor(private readonly handlers: ClientHandlers) {}

  getState(): { state: ConnectionState; attempt: number; lastFrameAt: number | null } {
    return { state: this.state, attempt: this.attempt, lastFrameAt: this.lastFrameAt };
  }

  /** Switch to a new access token; reconnect immediately when the socket is live. */
  updateToken(token: string): void {
    if (this.disposed) return;
    if (token === this.token) return;
    const wasActive = this.state === 'open' || this.state === 'connecting' || this.state === 'reconnecting';
    this.token = token;
    if (wasActive) {
      this.manualClose = true;
      this.teardownSocket();
      this.clearTimers();
      this.attempt = 0;
      this.manualClose = false;
      this.open();
    }
  }

  connect(): void {
    if (this.disposed) return;
    const token = this.handlers.getToken();
    if (!token) {
      this.setState('closed');
      return;
    }
    this.token = token;
    this.manualClose = false;
    this.open();
  }

  disconnect(): void {
    this.disposed = true;
    this.manualClose = true;
    this.clearTimers();
    this.teardownSocket();
    this.setState('closed');
  }

  /** Force an immediate reconnect, bypassing backoff (used by the UI button). */
  reconnectNow(): void {
    if (this.disposed) return;
    this.clearTimers();
    this.teardownSocket();
    this.attempt = 0;
    this.manualClose = false;
    this.open();
  }

  private setState(state: ConnectionState): void {
    this.state = state;
    this.handlers.onState(state, { attempt: this.attempt, lastFrameAt: this.lastFrameAt });
  }

  private open(): void {
    if (this.disposed) return;
    if (!this.token) {
      this.setState('closed');
      return;
    }
    this.setState(this.attempt > 0 ? 'reconnecting' : 'connecting');
    let socket: WebSocket;
    try {
      socket = new WebSocket(resolveWebSocketUrl(this.token));
    } catch {
      this.scheduleReconnect();
      return;
    }
    this.socket = socket;

    socket.onopen = () => {
      this.attempt = 0;
      this.setState('open');
      this.startHeartbeat();
    };

    socket.onmessage = (event: MessageEvent<string>) => {
      this.lastFrameAt = Date.now();
      let frame: WsFrame;
      try {
        frame = JSON.parse(event.data) as WsFrame;
      } catch {
        return;
      }
      if (!frame || typeof frame.type !== 'string') return;
      this.handlers.onFrame(frame);
    };

    socket.onerror = () => {
      // The close event that follows carries the reconnect decision.
    };

    socket.onclose = () => {
      if (this.socket !== socket) return;
      this.socket = null;
      this.stopHeartbeat();
      if (this.manualClose || this.disposed) {
        this.setState('closed');
        return;
      }
      this.scheduleReconnect();
    };
  }

  private startHeartbeat(): void {
    this.stopHeartbeat();
    this.heartbeatTimer = setInterval(() => {
      if (this.socket?.readyState !== WebSocket.OPEN) return;
      if (this.lastFrameAt && Date.now() - this.lastFrameAt > STALE_SOCKET_MS) {
        // Silent socket: force a fresh handshake rather than waiting for TCP timeouts.
        this.teardownSocket();
        this.scheduleReconnect();
        return;
      }
      try {
        this.socket.send(JSON.stringify({ type: 'ping' }));
      } catch {
        // onclose handles recovery.
      }
    }, HEARTBEAT_MS);
  }

  private stopHeartbeat(): void {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
  }

  private clearTimers(): void {
    this.stopHeartbeat();
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
  }

  private teardownSocket(): void {
    const socket = this.socket;
    this.socket = null;
    if (!socket) return;
    socket.onopen = null;
    socket.onmessage = null;
    socket.onerror = null;
    socket.onclose = null;
    try {
      socket.close(1000, 'client');
    } catch {
      // Already closed.
    }
  }

  private scheduleReconnect(): void {
    if (this.disposed || this.manualClose) return;
    this.stopHeartbeat();
    this.attempt += 1;
    this.setState('reconnecting');
    const delay = Math.min(MAX_BACKOFF_MS, 1000 * 2 ** Math.min(this.attempt - 1, 5)) + Math.random() * 400;
    this.reconnectTimer = setTimeout(async () => {
      this.reconnectTimer = null;
      if (this.disposed || this.manualClose) return;
      // Refresh credentials when reconnecting more than once or when the token
      // expires inside the next two minutes.
      const exp = decodeJwt(this.token)?.exp;
      const expiringSoon = typeof exp === 'number' && exp * 1000 - Date.now() < 120_000;
      if (this.attempt >= 2 || expiringSoon) {
        const fresh = await refreshAccessToken();
        if (fresh) this.token = fresh;
        else {
          const latest = this.handlers.getToken();
          if (latest) this.token = latest;
        }
      }
      this.open();
    }, delay);
  }
}
