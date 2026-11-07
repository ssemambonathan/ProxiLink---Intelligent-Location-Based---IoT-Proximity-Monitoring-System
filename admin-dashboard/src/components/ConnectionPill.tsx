import { Link } from 'react-router-dom';
import { Wifi, WifiOff } from 'lucide-react';
import { useRealtime } from '@/context/RealtimeContext';
import { cn } from '@/lib/utils';
import type { ConnectionState } from '@/lib/ws';

export function connectionMeta(
  state: ConnectionState,
  paused: boolean,
  attempt: number,
): { label: string; tone: 'success' | 'warning' | 'neutral' | 'danger'; detail: string } {
  if (paused) {
    return { label: 'Paused', tone: 'warning', detail: 'Live feed paused — updates resume when you continue.' };
  }
  switch (state) {
    case 'open':
      return { label: 'Live', tone: 'success', detail: 'WebSocket connected. Receiving realtime frames.' };
    case 'connecting':
      return { label: 'Connecting', tone: 'warning', detail: 'Opening the realtime channel…' };
    case 'reconnecting':
      return {
        label: 'Reconnecting',
        tone: 'warning',
        detail: `Connection lost. Reconnecting attempt ${attempt} with exponential backoff.`,
      };
    case 'closed':
      return { label: 'Offline', tone: 'danger', detail: 'Realtime channel closed.' };
    default:
      return { label: 'Idle', tone: 'neutral', detail: 'Realtime channel not started.' };
  }
}

const toneStyles = {
  success: 'border-emerald-400/25 bg-emerald-400/10 text-emerald-300',
  warning: 'border-amber-400/25 bg-amber-400/10 text-amber-300',
  neutral: 'border-white/10 bg-white/[0.05] text-slate-400',
  danger: 'border-rose-400/25 bg-rose-400/10 text-rose-300',
};

const dotStyles = {
  success: 'bg-emerald-400',
  warning: 'bg-amber-400',
  neutral: 'bg-slate-500',
  danger: 'bg-rose-400',
};

/**
 * Compact realtime indicator used in the top bar. Doubles as a shortcut to the
 * live monitoring page where reconnect controls live.
 */
export function ConnectionPill({ className }: { className?: string }) {
  const { connection, attempt, paused } = useRealtime();
  const meta = connectionMeta(connection, paused, attempt);
  const Icon = meta.tone === 'success' ? Wifi : WifiOff;
  return (
    <Link
      to="/live"
      className={cn(
        'inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-[11px] font-medium transition hover:brightness-110',
        toneStyles[meta.tone],
        className,
      )}
      title={meta.detail}
    >
      <span className="relative flex h-1.5 w-1.5">
        {meta.tone === 'success' && (
          <span className={cn('absolute inline-flex h-full w-full animate-ping rounded-full opacity-60', dotStyles[meta.tone])} />
        )}
        <span className={cn('relative inline-flex h-1.5 w-1.5 rounded-full', dotStyles[meta.tone])} />
      </span>
      <Icon className="hidden h-3 w-3 sm:block" aria-hidden="true" />
      {meta.label}
    </Link>
  );
}
