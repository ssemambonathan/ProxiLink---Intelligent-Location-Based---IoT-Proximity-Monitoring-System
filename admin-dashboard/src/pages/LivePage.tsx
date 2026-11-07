import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Activity,
  ArrowUpRight,
  Cpu,
  MapPin,
  Pause,
  Play,
  Radio,
  RadioTower,
  RefreshCw,
  RotateCw,
  Wifi,
  WifiOff,
} from 'lucide-react';
import { useApi, useRecentIds } from '@/hooks/useApi';
import { useRealtime } from '@/context/RealtimeContext';
import type { Device, LiveSnapshot } from '@/lib/types';
import { categoryLabel, statusMeta } from '@/lib/meta';
import { formatDateTime, formatDbm, relativeTime } from '@/lib/format';
import { cardWrap, tdClass, thClass } from '@/lib/styles';
import { cn } from '@/lib/utils';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card, CardBody, CardHeader } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { SignalBars, StatusChip } from '@/components/ui/Badge';
import { TableSkeleton } from '@/components/ui/Skeleton';
import { EmptyState, ErrorState } from '@/components/ui/States';
import { LocationEventRow, ProximityEventRow } from '@/components/EventRows';
import { connectionMeta } from '@/components/ConnectionPill';
import { DeviceDetailsDrawer } from '@/components/DeviceDetailsDrawer';

function mergeEvents<T extends { id: string; recordedAt: string }>(incoming: T[], snapshot: T[], limit: number): T[] {
  const map = new Map<string, T>();
  [...incoming, ...snapshot].forEach((event) => {
    if (!map.has(event.id)) map.set(event.id, event);
  });
  return [...map.values()]
    .sort((a, b) => new Date(b.recordedAt).getTime() - new Date(a.recordedAt).getTime())
    .slice(0, limit);
}

export function LivePage() {
  const live = useApi<LiveSnapshot>('/dashboard/live', { pollMs: 60_000 });
  const {
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
  } = useRealtime();
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [markProximity, recentProximity] = useRecentIds(2800);
  const [markLocation, recentLocation] = useRecentIds(2800);

  const connectionState = connectionMeta(connection, paused, attempt);

  const devices = useMemo<Device[]>(() => {
    const base = live.data?.devices ?? [];
    return base.map((device) => {
      const update = deviceStatuses[device.id];
      if (!update) return device;
      return {
        ...device,
        status: update.status,
        lastRssi: update.lastRssi ?? device.lastRssi,
        lastSeenAt: update.lastSeenAt ?? device.lastSeenAt,
      };
    });
  }, [live.data, deviceStatuses]);

  const proximityFeed = useMemo(
    () => mergeEvents(proximityEvents, live.data?.recentProximity ?? [], 18),
    [proximityEvents, live.data],
  );
  const locationFeed = useMemo(
    () => mergeEvents(locationEvents, live.data?.recentLocations ?? [], 18),
    [locationEvents, live.data],
  );

  useEffect(() => {
    if (paused || proximityEvents.length === 0) return;
    markProximity(proximityEvents.slice(0, 4).map((event) => event.id));
  }, [proximityEvents, paused, markProximity]);

  useEffect(() => {
    if (paused || locationEvents.length === 0) return;
    markLocation(locationEvents.slice(0, 4).map((event) => event.id));
  }, [locationEvents, paused, markLocation]);

  const counts = useMemo(() => {
    return devices.reduce(
      (acc, device) => {
        if (device.status === 'ONLINE') acc.online += 1;
        else if (device.status === 'NEARBY') acc.nearby += 1;
        else if (device.status === 'OFFLINE') acc.offline += 1;
        return acc;
      },
      { online: 0, nearby: 0, offline: 0 },
    );
  }, [devices]);

  const selectedSummary = devices.find((device) => device.id === selectedId) ?? null;

  return (
    <div className={cardWrap}>
      <PageHeader
        eyebrow="Monitor"
        title="Live monitoring"
        description="The realtime channel fans out proximity, location and device-status frames. This view merges those frames with a REST snapshot."
        actions={
          <>
            <Button variant="secondary" size="sm" onClick={live.reload} loading={live.refreshing} leftIcon={<RefreshCw className="h-3.5 w-3.5" />}>
              Refresh snapshot
            </Button>
            <Link to="/map">
              <Button size="sm" leftIcon={<MapPin className="h-3.5 w-3.5" />} rightIcon={<ArrowUpRight className="h-3.5 w-3.5" />}>
                Open map
              </Button>
            </Link>
          </>
        }
      />

      {/* Realtime channel */}
      <Card className={cn('overflow-hidden', connectionState.tone === 'success' ? 'border-emerald-400/15' : connectionState.tone === 'warning' ? 'border-amber-400/15' : 'border-white/[0.07]')}>
        <div className="flex flex-col gap-4 p-4 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-start gap-3">
            <span
              className={cn(
                'relative mt-0.5 flex h-11 w-11 shrink-0 items-center justify-center rounded-xl border',
                connectionState.tone === 'success'
                  ? 'border-emerald-400/25 bg-emerald-400/10 text-emerald-300'
                  : connectionState.tone === 'warning'
                    ? 'border-amber-400/25 bg-amber-400/10 text-amber-300'
                    : 'border-rose-400/25 bg-rose-400/10 text-rose-300',
              )}
            >
              {connectionState.tone === 'success' ? <Wifi className="h-5 w-5" aria-hidden="true" /> : <WifiOff className="h-5 w-5" aria-hidden="true" />}
              {connectionState.tone === 'warning' && (
                <span className="absolute inset-0 animate-pulse rounded-xl border border-amber-300/30" aria-hidden="true" />
              )}
            </span>
            <div>
              <div className="flex flex-wrap items-center gap-2">
                <h2 className="text-sm font-semibold text-slate-100">Realtime channel</h2>
                <span
                  className={cn(
                    'inline-flex items-center gap-1.5 rounded-full border px-2 py-0.5 text-[10px] font-medium',
                    connectionState.tone === 'success'
                      ? 'border-emerald-400/25 bg-emerald-400/10 text-emerald-300'
                      : connectionState.tone === 'warning'
                        ? 'border-amber-400/25 bg-amber-400/10 text-amber-300'
                        : 'border-rose-400/25 bg-rose-400/10 text-rose-300',
                  )}
                >
                  <span className={cn('h-1.5 w-1.5 rounded-full', connectionState.tone === 'success' ? 'bg-emerald-400' : connectionState.tone === 'warning' ? 'bg-amber-400' : 'bg-rose-400')} />
                  {connectionState.label}
                </span>
                {paused && <span className="text-[10px] font-medium text-amber-300">Feed paused</span>}
              </div>
              <p className="mt-1 max-w-xl text-xs leading-relaxed text-slate-400">{connectionState.detail}</p>
              <div className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1 text-[10px] text-slate-500">
                <span>Attempt <span className="font-mono text-slate-400">{attempt}</span></span>
                <span>Last frame <span className="text-slate-400">{lastFrameAt ? relativeTime(new Date(lastFrameAt)) : 'awaiting first frame'}</span></span>
                <span>Server time <span className="font-mono text-slate-400">{serverTime ? formatDateTime(serverTime) : '—'}</span></span>
                <span>Buffered <span className="font-mono text-slate-400">{proximityEvents.length + locationEvents.length}</span></span>
              </div>
            </div>
          </div>
          <div className="flex shrink-0 flex-wrap items-center gap-2">
            <Button
              variant="secondary"
              size="sm"
              onClick={reconnect}
              leftIcon={<RotateCw className="h-3.5 w-3.5" />}
              title="Close and reopen the WebSocket immediately"
            >
              Reconnect
            </Button>
            <Button
              variant={paused ? 'primary' : 'outline'}
              size="sm"
              onClick={() => setPaused(!paused)}
              leftIcon={paused ? <Play className="h-3.5 w-3.5" /> : <Pause className="h-3.5 w-3.5" />}
            >
              {paused ? 'Resume feed' : 'Pause feed'}
            </Button>
          </div>
        </div>
        <div className="flex flex-wrap items-center gap-x-5 gap-y-1.5 border-t border-white/[0.06] bg-navy-950/30 px-4 py-2 text-[10px] text-slate-600">
          <span>Heartbeat ping every 25s</span>
          <span>Exponential backoff 1–30s</span>
          <span>Token refresh on reconnect</span>
          <span className="font-mono text-slate-500">/ws?token=…</span>
          <span className="ml-auto">Frames: hello · proximity.event · location.event · device.status · notification</span>
        </div>
      </Card>

      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        {[
          { label: 'Devices in snapshot', value: devices.length, tone: 'text-slate-100' },
          { label: 'Online', value: counts.online, tone: 'text-emerald-300' },
          { label: 'Nearby band', value: counts.nearby, tone: 'text-cyan-300' },
          { label: 'Offline', value: counts.offline, tone: 'text-slate-400' },
        ].map((stat) => (
          <div key={stat.label} className="rounded-xl border border-white/[0.07] bg-white/[0.025] px-3.5 py-3">
            <p className="text-[10px] uppercase tracking-[0.12em] text-slate-500">{stat.label}</p>
            <p className={cn('mt-1 text-xl font-semibold tabular-nums tracking-tight', stat.tone)}>{stat.value}</p>
          </div>
        ))}
      </div>

      {/* Devices */}
      <Card>
        <CardHeader
          title="Live devices"
          description="Status, RSSI and last communication, updated by device.status frames"
          icon={Cpu}
          action={<span className="text-[10px] text-slate-500">{devices.length} devices</span>}
        />
        {live.error && !live.data ? (
          <div className="p-4">
            <ErrorState error={live.error} onRetry={live.reload} compact />
          </div>
        ) : live.loading && !live.data ? (
          <TableSkeleton rows={6} columns={6} />
        ) : devices.length === 0 ? (
          <div className="p-4">
            <EmptyState icon={Cpu} title="No devices online" description="Register devices and start monitoring from the Android app to populate the live table." />
          </div>
        ) : (
          <div className="max-h-[430px] overflow-auto">
            <table className="w-full min-w-[720px] border-collapse">
              <thead className="sticky top-0 z-10">
                <tr className="table-head">
                  <th className={thClass} scope="col">Device</th>
                  <th className={thClass} scope="col">Category</th>
                  <th className={thClass} scope="col">Status</th>
                  <th className={thClass} scope="col">Signal</th>
                  <th className={thClass} scope="col">Owner</th>
                  <th className={thClass} scope="col">Last communication</th>
                  <th className={cn(thClass, 'text-right')} scope="col">Details</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-white/[0.05]">
                {devices.map((device) => {
                  const meta = statusMeta(device.status);
                  return (
                    <tr key={device.id} className="row-hover">
                      <td className={tdClass}>
                        <span className="flex items-center gap-2">
                          <span className="h-2 w-2 rounded-full" style={{ backgroundColor: meta.hex }} aria-hidden="true" />
                          <span className="min-w-0">
                            <span className="block truncate text-xs font-medium text-slate-100">{device.name}</span>
                            <span className="block truncate font-mono text-[10px] text-slate-500">{device.hardwareId || device.id.slice(0, 8)}</span>
                          </span>
                        </span>
                      </td>
                      <td className={cn(tdClass, 'text-slate-400')}>{categoryLabel(device.category)}</td>
                      <td className={tdClass}><StatusChip status={device.status} /></td>
                      <td className={tdClass}>
                        <span className="inline-flex items-center gap-2">
                          <SignalBars rssi={device.lastRssi} />
                          <span className="font-mono text-[11px] text-slate-400">{formatDbm(device.lastRssi)}</span>
                        </span>
                      </td>
                      <td className={cn(tdClass, 'text-slate-400')}>{device.ownerName || '—'}</td>
                      <td className={tdClass}>
                        <span className="text-slate-300">{relativeTime(device.lastSeenAt)}</span>
                        <span className="block text-[10px] text-slate-600">{formatDateTime(device.lastSeenAt)}</span>
                      </td>
                      <td className={cn(tdClass, 'text-right')}>
                        <Button variant="ghost" size="sm" onClick={() => setSelectedId(device.id)}>
                          Inspect
                        </Button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {/* Feeds */}
      <div className="grid gap-4 lg:grid-cols-2">
        <Card className={paused ? 'opacity-80' : undefined}>
          <CardHeader
            title="Latest proximity events"
            description="Live WebSocket frames prepended to the snapshot"
            icon={RadioTower}
            action={
              <span className="inline-flex items-center gap-1.5 text-[10px] text-slate-500">
                <Radio className={cn('h-3 w-3', paused ? 'text-amber-400' : 'animate-pulse text-emerald-400')} aria-hidden="true" />
                {proximityFeed.length}
              </span>
            }
          />
          <CardBody className="max-h-[420px] overflow-y-auto p-2">
            {proximityFeed.length === 0 ? (
              <EmptyState compact icon={RadioTower} title="Waiting for proximity frames" description="Frames appear instantly when the Android app syncs a BLE scan." />
            ) : (
              <div className="space-y-0.5">
                {proximityFeed.map((event) => (
                  <ProximityEventRow key={event.id} event={event} highlight={recentProximity.has(event.id)} />
                ))}
              </div>
            )}
          </CardBody>
        </Card>

        <Card className={paused ? 'opacity-80' : undefined}>
          <CardHeader
            title="Latest location events"
            description="GPS fixes as they arrive"
            icon={MapPin}
            action={
              <span className="inline-flex items-center gap-1.5 text-[10px] text-slate-500">
                <Activity className={cn('h-3 w-3', paused ? 'text-amber-400' : 'animate-pulse text-cyan-400')} aria-hidden="true" />
                {locationFeed.length}
              </span>
            }
          />
          <CardBody className="max-h-[420px] overflow-y-auto p-2">
            {locationFeed.length === 0 ? (
              <EmptyState compact icon={MapPin} title="Waiting for location frames" description="No GPS fixes have been reported in this session." />
            ) : (
              <div className="space-y-0.5">
                {locationFeed.map((event) => (
                  <LocationEventRow key={event.id} event={event} highlight={recentLocation.has(event.id)} />
                ))}
              </div>
            )}
          </CardBody>
        </Card>
      </div>

      {live.error && live.data && <ErrorState error={live.error} onRetry={live.reload} compact />}

      <DeviceDetailsDrawer deviceId={selectedId} onClose={() => setSelectedId(null)} summary={selectedSummary} />
    </div>
  );
}

