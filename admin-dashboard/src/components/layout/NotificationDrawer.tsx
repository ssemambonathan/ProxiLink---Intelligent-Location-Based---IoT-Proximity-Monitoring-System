import { Bell, CheckCheck, Inbox, RefreshCw } from 'lucide-react';
import { useNotifications } from '@/context/NotificationsContext';
import { notificationTone, toneClasses } from '@/lib/meta';
import { relativeTime } from '@/lib/format';
import { cn } from '@/lib/utils';
import { Drawer } from '@/components/ui/Modal';
import { Button } from '@/components/ui/Button';
import { ErrorState } from '@/components/ui/States';
import { Skeleton } from '@/components/ui/Skeleton';

export function NotificationDrawer() {
  const {
    open,
    setOpen,
    items,
    unreadCount,
    loading,
    error,
    hasMore,
    loadMore,
    loadingMore,
    markRead,
    markAllRead,
    markingId,
    markingAll,
    refresh,
  } = useNotifications();

  return (
    <Drawer
      open={open}
      onClose={() => setOpen(false)}
      title="Notifications"
      description={unreadCount > 0 ? `${unreadCount} unread` : 'You are up to date'}
      widthClass="max-w-md"
      footer={
        <>
          <Button variant="ghost" size="sm" onClick={refresh} leftIcon={<RefreshCw className="h-3.5 w-3.5" />}>
            Refresh
          </Button>
          <Button
            variant="secondary"
            size="sm"
            onClick={() => void markAllRead()}
            disabled={unreadCount === 0}
            loading={markingAll}
            leftIcon={<CheckCheck className="h-3.5 w-3.5" />}
          >
            Mark all read
          </Button>
        </>
      }
    >
      {loading && items.length === 0 ? (
        <div className="space-y-2.5" role="status" aria-label="Loading notifications">
          {Array.from({ length: 5 }).map((_, index) => (
            <div key={index} className="rounded-xl border border-white/[0.06] p-3">
              <Skeleton className="h-3.5 w-2/3" />
              <Skeleton className="mt-2 h-3 w-full" />
              <Skeleton className="mt-1.5 h-3 w-20" />
            </div>
          ))}
        </div>
      ) : error ? (
        <ErrorState error={error} onRetry={refresh} compact />
      ) : items.length === 0 ? (
        <div className="flex flex-col items-center justify-center rounded-xl border border-dashed border-white/[0.08] px-5 py-12 text-center">
          <span className="flex h-11 w-11 items-center justify-center rounded-xl border border-white/[0.08] bg-white/[0.03] text-slate-500">
            <Inbox className="h-5 w-5" aria-hidden="true" />
          </span>
          <p className="mt-3 text-sm font-medium text-slate-200">Nothing here yet</p>
          <p className="mt-1 text-xs leading-relaxed text-slate-500">
            Proximity alerts and device notifications will appear in real time.
          </p>
        </div>
      ) : (
        <div className="space-y-2">
          {items.map((notification) => {
            const tone = notificationTone(notification.type);
            const style = toneClasses[tone];
            return (
              <div
                key={notification.id}
                className={cn(
                  'rounded-xl border p-3 transition-colors',
                  notification.read ? 'border-white/[0.06] bg-white/[0.015]' : 'border-white/[0.1] bg-white/[0.04]',
                )}
              >
                <div className="flex items-start gap-2.5">
                  <span className={cn('mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-lg border', style.chip)}>
                    <Bell className="h-3.5 w-3.5" aria-hidden="true" />
                  </span>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-start gap-2">
                      <p className={cn('text-[13px] leading-snug', notification.read ? 'text-slate-300' : 'font-medium text-slate-100')}>
                        {notification.title}
                      </p>
                      {!notification.read && <span className="mt-1 h-1.5 w-1.5 shrink-0 rounded-full bg-brand-400" aria-label="Unread" />}
                    </div>
                    {notification.body && <p className="mt-1 text-xs leading-relaxed text-slate-400">{notification.body}</p>}
                    <div className="mt-2 flex items-center justify-between gap-2">
                      <span className="text-[10px] uppercase tracking-wider text-slate-600" title={notification.createdAt}>
                        {relativeTime(notification.createdAt)} · {notification.type.replace(/_/g, ' ').toLowerCase()}
                      </span>
                      {!notification.read && (
                        <button
                          type="button"
                          onClick={() => void markRead(notification.id)}
                          disabled={markingId === notification.id}
                          className="rounded-md px-1.5 py-0.5 text-[10px] font-medium text-brand-300 transition hover:bg-brand-500/10 hover:text-brand-200 disabled:opacity-60"
                        >
                          {markingId === notification.id ? 'Saving…' : 'Mark read'}
                        </button>
                      )}
                    </div>
                  </div>
                </div>
              </div>
            );
          })}
          {hasMore && (
            <Button variant="ghost" size="sm" className="w-full" onClick={loadMore} loading={loadingMore}>
              Load older notifications
            </Button>
          )}
        </div>
      )}
    </Drawer>
  );
}
