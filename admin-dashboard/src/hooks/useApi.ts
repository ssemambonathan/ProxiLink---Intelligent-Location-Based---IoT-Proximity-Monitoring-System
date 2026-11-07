import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError, apiRequest } from '@/lib/api';

export interface UseApiOptions {
  enabled?: boolean;
  /** Poll interval in ms. Polling never renders a skeleton over existing data. */
  pollMs?: number;
}

export interface UseApiResult<T> {
  data: T | null;
  error: ApiError | null;
  loading: boolean;
  refreshing: boolean;
  reload: () => void;
  lastUpdatedAt: number | null;
}

/**
 * Small GET hook: request cancellation on unmount/path change, optional polling,
 * and stale data retention during refresh so tables do not flash.
 */
export function useApi<T>(path: string | null, options: UseApiOptions = {}): UseApiResult<T> {
  const { enabled = true, pollMs } = options;
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const [loading, setLoading] = useState<boolean>(Boolean(path) && enabled);
  const [refreshing, setRefreshing] = useState(false);
  const [lastUpdatedAt, setLastUpdatedAt] = useState<number | null>(null);
  const [nonce, setNonce] = useState(0);
  const requestId = useRef(0);
  // Latest data in a ref so the skeleton decision does not need to re-create `run`.
  const dataRef = useRef<T | null>(null);

  const run = useCallback(
    async (signal: AbortSignal, background: boolean) => {
      if (!path) return;
      const id = ++requestId.current;
      if (background) setRefreshing(true);
      else setLoading((current) => (dataRef.current === null ? true : current));
      try {
        const result = await apiRequest<T>(path, { signal });
        if (signal.aborted || id !== requestId.current) return;
        setData(result);
        setError(null);
        setLastUpdatedAt(Date.now());
      } catch (caught) {
        if (signal.aborted || id !== requestId.current) return;
        if (caught instanceof DOMException && caught.name === 'AbortError') return;
        setError(caught instanceof ApiError ? caught : new ApiError(0, 'UNKNOWN', 'Unexpected error'));
      } finally {
        if (!signal.aborted && id === requestId.current) {
          setLoading(false);
          setRefreshing(false);
        }
      }
    },
    [path],
  );

  useEffect(() => {
    dataRef.current = data;
  }, [data]);

  useEffect(() => {
    setData(null);
    setError(null);
    setLastUpdatedAt(null);
    setLoading(Boolean(path) && enabled);
    if (!path || !enabled) {
      setLoading(false);
      return;
    }
    const controller = new AbortController();
    void run(controller.signal, false);
    return () => controller.abort();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [path, enabled, nonce, run]);

  useEffect(() => {
    if (!path || !enabled || !pollMs) return;
    const controller = new AbortController();
    const timer = setInterval(() => {
      void run(controller.signal, true);
    }, pollMs);
    return () => {
      clearInterval(timer);
      controller.abort();
    };
  }, [path, enabled, pollMs, run]);

  const reload = useCallback(() => setNonce((value) => value + 1), []);

  return { data, error, loading, refreshing, reload, lastUpdatedAt };
}

export function useDebouncedValue<T>(value: T, delayMs = 350): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs);
    return () => clearTimeout(timer);
  }, [value, delayMs]);
  return debounced;
}

/** Tracks ids that arrived in the last `holdMs` to drive brief row highlights. */
export function useRecentIds(holdMs = 2500): [(ids: string[]) => void, Set<string>] {
  const [recent, setRecent] = useState<Set<string>>(new Set());
  const timers = useRef<Array<ReturnType<typeof setTimeout>>>([]);

  useEffect(() => {
    return () => {
      timers.current.forEach(clearTimeout);
    };
  }, []);

  const mark = useCallback(
    (ids: string[]) => {
      if (ids.length === 0) return;
      setRecent((current) => {
        const next = new Set(current);
        ids.forEach((id) => next.add(id));
        return next;
      });
      const timer = setTimeout(() => {
        setRecent((current) => {
          const next = new Set(current);
          ids.forEach((id) => next.delete(id));
          return next;
        });
      }, holdMs);
      timers.current.push(timer);
    },
    [holdMs],
  );

  return [mark, recent];
}
