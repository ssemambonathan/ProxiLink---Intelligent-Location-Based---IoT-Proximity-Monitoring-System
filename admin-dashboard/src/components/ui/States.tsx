import type { ReactNode } from 'react';
import { AlertTriangle, Inbox, RefreshCw, WifiOff } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { describeApiError, type ApiError } from '@/lib/api';
import { cn } from '@/lib/utils';
import { useConnectivity } from '@/context/ConnectivityContext';
import { Button } from '@/components/ui/Button';

export function EmptyState({
  icon: Icon = Inbox,
  title,
  description,
  action,
  className,
  compact = false,
}: {
  icon?: LucideIcon;
  title: string;
  description?: ReactNode;
  action?: ReactNode;
  className?: string;
  compact?: boolean;
}) {
  return (
    <div
      className={cn(
        'flex flex-col items-center justify-center rounded-xl border border-dashed border-white/[0.08] bg-white/[0.015] text-center',
        compact ? 'px-4 py-8' : 'px-6 py-14',
        className,
      )}
    >
      <span className="flex h-11 w-11 items-center justify-center rounded-xl border border-white/[0.08] bg-white/[0.03] text-slate-500">
        <Icon className="h-5 w-5" aria-hidden="true" />
      </span>
      <h3 className="mt-3 text-sm font-medium text-slate-200">{title}</h3>
      {description && <p className="mt-1 max-w-md text-xs leading-relaxed text-slate-500">{description}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  );
}

export function ErrorState({
  error,
  onRetry,
  className,
  compact = false,
}: {
  error: ApiError;
  onRetry?: () => void;
  className?: string;
  compact?: boolean;
}) {
  const copy = describeApiError(error);
  const Icon = error.network ? WifiOff : AlertTriangle;
  return (
    <div
      role="alert"
      className={cn(
        'flex flex-col items-start gap-3 rounded-xl border border-rose-400/20 bg-rose-500/[0.045]',
        compact ? 'p-3.5' : 'p-5',
        className,
      )}
    >
      <div className="flex items-start gap-3">
        <span className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg border border-rose-400/25 bg-rose-500/10 text-rose-300">
          <Icon className="h-4 w-4" aria-hidden="true" />
        </span>
        <div className="min-w-0">
          <p className="text-sm font-medium text-slate-100">{copy.title}</p>
          <p className="mt-1 text-xs leading-relaxed text-slate-400">{copy.description}</p>
          {Object.keys(error.violations).length > 0 && (
            <ul className="mt-2 space-y-1 text-xs text-rose-200/90">
              {Object.entries(error.violations).map(([field, message]) => (
                <li key={field}>
                  <span className="font-medium">{field}</span>: {message}
                </li>
              ))}
            </ul>
          )}
          <p className="mt-2 font-mono text-[10px] uppercase tracking-wider text-slate-600">
            {error.code}
            {error.status > 0 ? ` · HTTP ${error.status}` : ''}
          </p>
        </div>
      </div>
      {onRetry && (
        <Button
          variant="secondary"
          size="sm"
          onClick={onRetry}
          leftIcon={<RefreshCw className="h-3.5 w-3.5" aria-hidden="true" />}
          className="ml-11"
        >
          Retry
        </Button>
      )}
    </div>
  );
}

/**
 * App-wide banner shown once an API call fails at the network layer. It avoids
 * duplicating "connection failed" warnings in every card.
 */
export function ConnectivityBanner({ className }: { className?: string }) {
  const { online, checking, recheck } = useConnectivity();
  if (online) return null;
  return (
    <div
      role="status"
      className={cn(
        'flex flex-wrap items-center gap-x-3 gap-y-2 border-b border-amber-400/20 bg-amber-500/[0.07] px-4 py-2 text-xs text-amber-100 sm:px-6',
        className,
      )}
    >
      <WifiOff className="h-3.5 w-3.5 shrink-0 text-amber-300" aria-hidden="true" />
      <span className="font-medium text-amber-200">API unreachable</span>
      <span className="text-amber-100/70">
        The console cannot reach the ProxiLink backend. Showing live server data only — no placeholder values.
      </span>
      <button
        type="button"
        onClick={() => void recheck()}
        disabled={checking}
        className="ml-auto inline-flex items-center gap-1.5 rounded-md border border-amber-300/30 px-2 py-1 font-medium text-amber-100 transition hover:bg-amber-300/10 disabled:opacity-60"
      >
        {checking ? 'Checking…' : 'Retry now'}
      </button>
    </div>
  );
}

export function InlineError({ children }: { children: ReactNode }) {
  return (
    <p className="rounded-lg border border-rose-400/25 bg-rose-500/[0.07] px-3 py-2 text-xs text-rose-200" role="alert">
      {children}
    </p>
  );
}
