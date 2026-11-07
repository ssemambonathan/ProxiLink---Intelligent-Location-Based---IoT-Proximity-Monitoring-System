import { Link2, MapPin, Radar, Rss as RssiIcon } from 'lucide-react';
import { formatCoords, formatDateTime, formatDbm, formatMeters, relativeTime } from '@/lib/format';
import type { LocationEvent, ProximityEvent } from '@/lib/types';
import { cn } from '@/lib/utils';
import { ProximityChip, SignalBars } from '@/components/ui/Badge';

function RowShell({
  children,
  highlight,
  accent,
}: {
  children: React.ReactNode;
  highlight?: boolean;
  accent?: string;
}) {
  return (
    <div
      className={cn(
        'relative flex gap-3 rounded-xl border border-transparent px-3 py-2.5 transition-colors',
        highlight ? 'animate-flash border-brand-400/25 bg-brand-500/[0.08]' : 'hover:bg-white/[0.03]',
      )}
    >
      {accent && <span className="absolute inset-y-2 left-0 w-[2px] rounded-full" style={{ backgroundColor: accent }} aria-hidden="true" />}
      {children}
    </div>
  );
}

export function ProximityEventRow({ event, highlight }: { event: ProximityEvent; highlight?: boolean }) {
  const rssi = event.smoothedRssi ?? event.rssi;
  return (
    <RowShell highlight={highlight} accent={event.proximityLevel === 'IMMEDIATE' ? '#FB7185' : undefined}>
      <span className="mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-lg border border-white/[0.07] bg-white/[0.03] text-brand-300">
        <RssiIcon className="h-3.5 w-3.5" aria-hidden="true" />
      </span>
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
          <p className="truncate text-[13px] font-medium text-slate-200">
            {event.deviceName || 'Unknown device'}
          </p>
          <ProximityChip level={event.proximityLevel} />
          <span className="ml-auto flex items-center gap-1.5">
            <SignalBars rssi={rssi} />
            <span className="font-mono text-[11px] text-slate-400">{formatDbm(rssi)}</span>
          </span>
        </div>
        <p className="mt-0.5 truncate text-[11px] text-slate-500">
          {event.userDisplayName ? `${event.userDisplayName} · ` : ''}
          {event.estimatedDistanceM != null ? `~${formatMeters(event.estimatedDistanceM)} est. · ` : ''}
          {formatDateTime(event.recordedAt)}
        </p>
      </div>
      <span className="shrink-0 self-center text-[10px] text-slate-600">{relativeTime(event.recordedAt)}</span>
    </RowShell>
  );
}

export function LocationEventRow({ event, highlight }: { event: LocationEvent; highlight?: boolean }) {
  return (
    <RowShell highlight={highlight}>
      <span className="mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-lg border border-white/[0.07] bg-white/[0.03] text-cyan-300">
        <MapPin className="h-3.5 w-3.5" aria-hidden="true" />
      </span>
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
          <p className="truncate text-[13px] font-medium text-slate-200">
            {event.userDisplayName || 'Location report'}
          </p>
          {event.deviceId && <span className="text-[10px] text-slate-500">device-linked</span>}
          <span className="ml-auto font-mono text-[11px] text-slate-400">{formatCoords(event.latitude, event.longitude)}</span>
        </div>
        <p className="mt-0.5 truncate text-[11px] text-slate-500">
          {event.accuracyM != null ? `±${event.accuracyM.toFixed(0)} m · ` : ''}
          {event.speedMps != null ? `${event.speedMps.toFixed(1)} m/s · ` : ''}
          {event.source ? `${event.source} · ` : ''}
          {formatDateTime(event.recordedAt)}
        </p>
      </div>
      <span className="shrink-0 self-center text-[10px] text-slate-600">{relativeTime(event.recordedAt)}</span>
    </RowShell>
  );
}

export function EventFeedFootnote({ count, hint }: { count: number; hint?: string }) {
  return (
    <div className="flex items-center justify-between px-3 pt-2 text-[10px] uppercase tracking-wider text-slate-600">
      <span className="inline-flex items-center gap-1.5">
        <Radar className="h-3 w-3" aria-hidden="true" /> {count} event{count === 1 ? '' : 's'}
      </span>
      {hint && <span className="inline-flex items-center gap-1"><Link2 className="h-3 w-3" aria-hidden="true" />{hint}</span>}
    </div>
  );
}
