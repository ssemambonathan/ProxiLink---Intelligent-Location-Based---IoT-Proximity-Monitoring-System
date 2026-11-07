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
import { ApiError, apiRequest, isApiError, errorMessage } from '@/lib/api';
import type { AppNotification, Page } from '@/lib/types';
import { useAuth } from '@/context/AuthContext';
import { useRealtime } from '@/context/RealtimeContext';
import { useToast } from '@/context/ToastContext';

interface NotificationsValue {
  items: AppNotification[];
  unreadCount: number;
  loading: boolean;
  loadingMore: boolean;
  error: ApiError | null;
  hasMore: boolean;
  open: boolean;
  setOpen: (open: boolean) => void;
  openDrawer: () => void;
  refresh: () => void;
  loadMore: () => void;
  markRead: (id: string) => Promise<void>;
  markAllRead: () => Promise<void>;
  markingId: string | null;
  markingAll: boolean;
}

const NotificationsContext = createContext<NotificationsValue | null>(null);

const PAGE_SIZE = 20;

function mergeNotification(items: AppNotification[], incoming: AppNotification): AppNotification[] {
  const next = [incoming, ...items.filter((item) => item.id !== incoming.id)];
  return next.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
}

export function NotificationsProvider({ children }: { children: ReactNode }) {
  const { status } = useAuth();
  const { lastNotification } = useRealtime();
  const toast = useToast();

  const [items, setItems] = useState<AppNotification[]>([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [loading, setLoading] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);
  const [hasMore, setHasMore] = useState(false);
  const [page, setPage] = useState(0);
  const [open, setOpen] = useState(false);
  const [markingId, setMarkingId] = useState<string | null>(null);
  const [markingAll, setMarkingAll] = useState(false);
  const handledFrame = useRef<string | null>(null);

  const refreshUnread = useCallback(async () => {
    try {
      const result = await apiRequest<Page<AppNotification>>('/notifications', {
        query: { unreadOnly: true, page: 0, size: 1 },
      });
      setUnreadCount(result.totalElements);
    } catch {
      // The drawer shows its own error state; the badge simply stays put.
    }
  }, []);

  const loadPage = useCallback(
    async (targetPage: number, mode: 'replace' | 'append') => {
      if (mode === 'append') setLoadingMore(true);
      else setLoading(true);
      try {
        const result = await apiRequest<Page<AppNotification>>('/notifications', {
          query: { page: targetPage, size: PAGE_SIZE },
        });
        setItems((current) => {
          if (mode === 'append') {
            const seen = new Set(current.map((item) => item.id));
            return [...current, ...result.items.filter((item) => !seen.has(item.id))];
          }
          return result.items;
        });
        setPage(result.page ?? targetPage);
        setHasMore((result.page ?? targetPage) + 1 < result.totalPages);
        setError(null);
      } catch (caught) {
        if (caught instanceof DOMException && caught.name === 'AbortError') return;
        if (isApiError(caught)) setError(caught);
      } finally {
        setLoading(false);
        setLoadingMore(false);
      }
    },
    [],
  );

  useEffect(() => {
    if (status !== 'authenticated') {
      setItems([]);
      setUnreadCount(0);
      setError(null);
      setOpen(false);
      return;
    }
    void refreshUnread();
  }, [status, refreshUnread]);

  // Refresh the list each time the drawer opens so it reflects server state.
  const openDrawer = useCallback(() => {
    setOpen(true);
    void loadPage(0, 'replace');
    void refreshUnread();
  }, [loadPage, refreshUnread]);

  useEffect(() => {
    if (!lastNotification) return;
    if (handledFrame.current === lastNotification.id) return;
    handledFrame.current = lastNotification.id;
    setItems((current) => mergeNotification(current, lastNotification));
    if (!lastNotification.read) {
      setUnreadCount((count) => count + 1);
      toast.info(lastNotification.title, lastNotification.body ?? undefined);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [lastNotification]);

  const markRead = useCallback(
    async (id: string) => {
      const target = items.find((item) => item.id === id);
      if (!target || target.read) return;
      setMarkingId(id);
      try {
        await apiRequest<void>(`/notifications/${id}/read`, { method: 'POST' });
        setItems((current) => current.map((item) => (item.id === id ? { ...item, read: true } : item)));
        setUnreadCount((count) => Math.max(0, count - 1));
      } catch (caught) {
        toast.error('Could not mark as read', errorMessage(caught));
      } finally {
        setMarkingId(null);
      }
    },
    [items, toast],
  );

  const markAllRead = useCallback(async () => {
    setMarkingAll(true);
    try {
      await apiRequest<void>('/notifications/read-all', { method: 'POST' });
      setItems((current) => current.map((item) => ({ ...item, read: true })));
      setUnreadCount(0);
      toast.success('All notifications marked as read');
    } catch (caught) {
      toast.error('Could not mark all as read', errorMessage(caught));
    } finally {
      setMarkingAll(false);
    }
  }, [toast]);

  const loadMore = useCallback(() => {
    if (loadingMore || !hasMore) return;
    void loadPage(page + 1, 'append');
  }, [hasMore, loadPage, loadingMore, page]);

  const refresh = useCallback(() => {
    void loadPage(0, 'replace');
    void refreshUnread();
  }, [loadPage, refreshUnread]);

  const value = useMemo<NotificationsValue>(
    () => ({
      items,
      unreadCount,
      loading,
      loadingMore,
      error,
      hasMore,
      open,
      setOpen,
      openDrawer,
      refresh,
      loadMore,
      markRead,
      markAllRead,
      markingId,
      markingAll,
    }),
    [
      items,
      unreadCount,
      loading,
      loadingMore,
      error,
      hasMore,
      open,
      openDrawer,
      refresh,
      loadMore,
      markRead,
      markAllRead,
      markingId,
      markingAll,
    ],
  );

  return <NotificationsContext.Provider value={value}>{children}</NotificationsContext.Provider>;
}

export function useNotifications(): NotificationsValue {
  const context = useContext(NotificationsContext);
  if (!context) throw new Error('useNotifications must be used within a NotificationsProvider');
  return context;
}
