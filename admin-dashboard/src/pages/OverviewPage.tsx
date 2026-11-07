import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Activity,
  ArrowUpRight,
  Bell,
  Cpu,
  MapPin,
  RadioTower,
  RefreshCw,
  Sparkles,
  Wifi,
  WifiOff,
} from 'lucide-react';
import {
  Area,
  CartesianGrid,
  Cell,
  ComposedChart,
  Line,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { format, parseISO } from 'date-fns';
import { useApi } from '@/hooks/useApi';
import type {
  Device,
  LocationEvent,
  Overview,
  Page,
  ProximityEvent,
  SystemStatus,
  TrendsResponse,
} from '@/lib/types';
import { categoryColor, categoryLabel, deviceStatusMeta } from '@/lib/meta';
import { formatCompact, formatDateTime, formatUptime, relativeTime } from '@/lib/format';
import { cardWrap } from '@/lib/styles';
import { cn } from '@/lib/utils';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card, CardBody, CardHeader } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { StatCard } from '@/components/ui/StatCard';
import { StatGridSkeleton, Skeleton } from '@/components/ui/Skeleton';
import { EmptyState, ErrorState } from '@/components/ui/States';
import { ProximityEventRow, LocationEventRow } from '@/components/EventRows';
import { DeviceMap, toMappableDevices } from '@/components/map/DeviceMap';
import { ChartTooltip, chartAxisLine, chartAxisTick, chartGridStroke } from '@/components/charts/ChartTooltip';

type FeedTab = 'proximity' | 'location';

function trendDateLabel(value: string): string {
  try {
    return format(parseISO(value), 'EEE d');
  } catch {
    return value;
  }
}

function ServicePill({ label, value }: { label: string; value: string | undefined }) {
  const normalized = (value ?? '').toUpperCase();
  const tone =
    normalized === 'UP'
      ? 'border-emerald-400/25 bg-emerald-400/10 text-emerald-300'
      : normalized === 'DISABLED'
        ? 'border-white/10 bg-white/[0.04] text-slate-400'
        : normalized === 'DOWN' || normalized === ''
          ? 'border-rose-400/25 bg-rose-400/10 text-rose-300'
          : 'border-amber-400/25 bg-amber-400/10 text-amber-300';
  return (
    <div className="flex items-center gap-2">
      <span className="text-[10px] uppercase tracking-wider text-slate-500">{label}</span>
      <span className={cn('inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-[10px] font-medium', tone)}>
        <span
          className={cn(
            'h-1 w-1 rounded-full',
            normalized === 'UP' ? 'bg-emerald-400' : normalized === 'DISABLED' ? 'bg-slate-500' : normalized === 'DOWN' ? 'bg-rose-400' : 'bg-amber-400',
          )}
        />
        {normalized || 'UNKNOWN'}
      </span>
    </div>
  );
}

export function OverviewPage() {
  const [feedTab, setFeedTab] = useState<FeedTab>('proximity');
  const overview = useApi<Overview>('/dashboard/overview', { pollMs: 30_000 });
  const trends = useApi<TrendsResponse>('/analytics/trends?days=7', { pollMs: 60_000 });
  const system = useApi<SystemStatus>('/system/status', { pollMs: 60_000 });
  const devices = useApi<Page<Device>>('/devices?page=0&size=100&active=true', { pollMs: 60_000 });
  const proximity = useApi<Page<ProximityEvent>>('/events/proximity?page=0&size=8', { pollMs: 30_000 });
  const locations = useApi<Page<LocationEvent>>('/events/location?page=0&size=8', { pollMs: 30_000 });

  const statsLoading = overview.loading && !overview.data;
  const o = overview.data;
  const fleet = o?.totalDevices ?? 0;
  const onlinePct = fleet > 0 ? Math.round(((o?.onlineDevices ?? 0) / fleet) * 100) : null;
  const offlinePct = fleet > 0 ? Math.round(((o?.offlineDevices ?? 0) / fleet) * 100) : null;

  const mappable = useMemo(() => toMappableDevices(devices.data?.items ?? []), [devices.data]);
  const categoryData = useMemo(
    () => (o?.devicesByCategory ?? []).filter((entry) => entry.count > 0),
    [o],
  );
  const categoryTotal = categoryData.reduce((sum, entry) => sum + entry.count, 0);
  const trendPoints = trends.data?.points ?? [];
  const hasTrendData = trendPoints.some((point) => point.proximityEvents > 0 || point.locationEvents > 0);
  const feed = feedTab === 'proximity' ? proximity : locations;

  const reloadAll = () => {
    overview.reload();
    trends.reload();
    system.reload();
    devices.reload();
    proximity.reload();
    locations.reload();
  };

  return (
    <div className={cardWrap}>
      <PageHeader
        eyebrow="Monitor"
        title="Overview"
        description="Fleet health, proximity activity and platform status at a glance. Every figure comes from the ProxiLink API."
        actions={
          <>
            <span className="hidden text-[11px] text-slate-500 sm:inline">
              {overview.lastUpdatedAt ? `Updated ${relativeTime(new Date(overview.lastUpdatedAt))}` : 'Awaiting data'}
            </span>
            <Button variant="secondary" size="sm" onClick={reloadAll} leftIcon={<RefreshCw className="h-3.5 w-3.5" />}>
              Refresh
            </Button>
            <Link to="/map">
              <Button size="sm" leftIcon={<MapPin className="h-3.5 w-3.5" />} rightIcon={<ArrowUpRight className="h-3.5 w-3.5" />}>
                Live map
              </Button>
            </Link>
          </>
        }
      />

      {/* Summary */}
      {overview.error && !o ? (
        <ErrorState error={overview.error} onRetry={overview.reload} />
      ) : statsLoading ? (
        <StatGridSkeleton count={8} />
      ) : (
        <div className="grid grid-cols-2 gap-3 sm:gap-4 lg:grid-cols-4">
          <StatCard
            className="col-span-2"
            label="Total devices"
            value={o?.totalDevices ?? null}
            icon={Cpu}
            tone="brand"
            hint={o ? `${o.discoveredToday} discovered today` : undefined}
          />
          <StatCard
            label="Online"
            value={o?.onlineDevices ?? null}
            icon={Wifi}
            tone="success"
            hint={onlinePct !== null ? `${onlinePct}% of fleet` : undefined}
          />
          <StatCard
            label="Offline"
            value={o?.offlineDevices ?? null}
            icon={WifiOff}
            tone="neutral"
            hint={offlinePct !== null ? `${offlinePct}% of fleet` : undefined}
          />
          <StatCard
            label="Active proximity"
            value={o?.activeProximityEvents ?? null}
            icon={RadioTower}
            tone="warning"
            hint="Currently banded"
          />
          <StatCard
            label="Location events · 24h"
            value={o?.recentLocationEvents24h ?? null}
            icon={MapPin}
            tone="info"
            hint="GPS reports"
          />
          <StatCard
            label="Unread notifications"
            value={o?.unreadNotifications ?? null}
            icon={Bell}
            tone="violet"
            hint="Bell to open"
          />
          <StatCard
            label="Discovered today"
            value={o?.discoveredToday ?? null}
            icon={Sparkles}
            tone="brand"
            hint="New registrations"
          />
        </div>
      )}

      {overview.error && o && (
        <ErrorState error={overview.error} onRetry={overview.reload} compact />
      )}

      {/* Trend + categories */}
      <div className="grid gap-4 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader
            title="7-day activity"
            description="Proximity and location events per day, with unique devices reporting"
            icon={Activity}
            action={
              <span className="hidden items-center gap-3 text-[10px] text-slate-500 sm:flex">
                <span className="inline-flex items-center gap-1.5"><span className="h-1.5 w-1.5 rounded-full bg-brand-400" />Proximity</span>
                <span className="inline-flex items-center gap-1.5"><span className="h-1.5 w-1.5 rounded-full bg-accent-500" />Location</span>
                <span className="inline-flex items-center gap-1.5"><span className="h-1.5 w-1.5 rounded-full bg-violet-400" />Devices</span>
              </span>
            }
          />
          <CardBody className="pt-3">
            {trends.loading && !trends.data ? (
              <Skeleton className="h-[260px] w-full" />
            ) : trends.error ? (
              <ErrorState error={trends.error} onRetry={trends.reload} compact />
            ) : !hasTrendData ? (
              <EmptyState
                compact
                icon={Activity}
                title="No event activity yet"
                description="Trends populate once the Android app syncs proximity or location batches."
              />
            ) : (
              <div style={{ height: 268 }}>
                <ResponsiveContainer width="100%" height="100%">
                  <ComposedChart data={trendPoints} margin={{ top: 8, right: 4, left: -18, bottom: 0 }}>
                    <defs>
                      <linearGradient id="overviewProximity" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="0%" stopColor="#5B8CFF" stopOpacity={0.35} />
                        <stop offset="100%" stopColor="#5B8CFF" stopOpacity={0.02} />
                      </linearGradient>
                      <linearGradient id="overviewLocation" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="0%" stopColor="#22D3EE" stopOpacity={0.28} />
                        <stop offset="100%" stopColor="#22D3EE" stopOpacity={0.02} />
                      </linearGradient>
                    </defs>
                    <CartesianGrid stroke={chartGridStroke} vertical={false} />
                    <XAxis dataKey="date" tick={chartAxisTick} axisLine={chartAxisLine} tickLine={false} tickFormatter={trendDateLabel} minTickGap={16} />
                    <YAxis yAxisId="events" tick={chartAxisTick} axisLine={false} tickLine={false} width={44} tickFormatter={(value: number) => formatCompact(value)} />
                    <YAxis yAxisId="devices" orientation="right" tick={chartAxisTick} axisLine={false} tickLine={false} width={32} allowDecimals={false} />
                    <Tooltip
                      content={<ChartTooltip labelFormatter={(value) => trendDateLabel(String(value))} />}
                      cursor={{ stroke: 'rgba(148,163,184,0.25)', strokeDasharray: '3 3' }}
                    />
                    <Area yAxisId="events" type="monotone" dataKey="proximityEvents" name="Proximity events" stroke="#5B8CFF" strokeWidth={2} fill="url(#overviewProximity)" />
                    <Area yAxisId="events" type="monotone" dataKey="locationEvents" name="Location events" stroke="#22D3EE" strokeWidth={2} fill="url(#overviewLocation)" />
                    <Line yAxisId="devices" type="monotone" dataKey="uniqueDevices" name="Unique devices" stroke="#A78BFA" strokeWidth={1.75} dot={false} strokeDasharray="5 4" />
                  </ComposedChart>
                </ResponsiveContainer>
              </div>
            )}
          </CardBody>
        </Card>

        <Card>
          <CardHeader title="Fleet by category" description="Registered devices" icon={Cpu} />
          <CardBody>
            {overview.error && !o ? (
              <ErrorState error={overview.error} onRetry={overview.reload} compact />
            ) : statsLoading ? (
              <Skeleton className="h-[280px] w-full" />
            ) : categoryData.length === 0 ? (
              <EmptyState compact icon={Cpu} title="No devices registered" description="Register a device to see the category mix." />
            ) : (
              <>
                <div className="relative" style={{ height: 176 }}>
                  <ResponsiveContainer width="100%" height="100%">
                    <PieChart>
                      <Pie
                        data={categoryData}
                        dataKey="count"
                        nameKey="category"
                        innerRadius={56}
                        outerRadius={78}
                        paddingAngle={2}
                        stroke="none"
                      >
                        {categoryData.map((entry) => (
                          <Cell key={String(entry.category)} fill={categoryColor(entry.category)} />
                        ))}
                      </Pie>
                      <Tooltip
                        content={<ChartTooltip labelFormatter={(value) => categoryLabel(String(value))} />}
                      />
                    </PieChart>
                  </ResponsiveContainer>
                  <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
                    <span className="text-2xl font-semibold tracking-tight text-slate-50">{categoryTotal}</span>
                    <span className="text-[10px] uppercase tracking-[0.16em] text-slate-500">devices</span>
                  </div>
                </div>
                <ul className="mt-2 space-y-2">
                  {categoryData.map((entry) => {
                    const share = categoryTotal > 0 ? Math.round((entry.count / categoryTotal) * 100) : 0;
                    return (
                      <li key={String(entry.category)} className="flex items-center gap-2">
                        <span className="h-2 w-2 rounded-full" style={{ backgroundColor: categoryColor(entry.category) }} aria-hidden="true" />
                        <span className="min-w-0 flex-1 truncate text-xs text-slate-300">{categoryLabel(entry.category)}</span>
                        <span className="text-[11px] tabular-nums text-slate-500">{entry.count}</span>
                        <span className="w-8 text-right text-[11px] tabular-nums text-slate-600">{share}%</span>
                      </li>
                    );
                  })}
                </ul>
              </>
            )}
          </CardBody>
        </Card>
      </div>

      {/* Map + feed */}
      <div className="grid gap-4 xl:grid-cols-5">
        <Card className="xl:col-span-3">
          <CardHeader
            title="Network map"
            description="Active devices with a reported GPS fix"
            icon={MapPin}
            action={
              <Link to="/map" className="inline-flex items-center gap-1 text-[11px] font-medium text-brand-300 transition hover:text-brand-200">
                Open map <ArrowUpRight className="h-3 w-3" />
              </Link>
            }
          />
          <CardBody className="p-0">
            {devices.error ? (
              <div className="p-4">
                <ErrorState error={devices.error} onRetry={devices.reload} compact />
              </div>
            ) : devices.loading && !devices.data ? (
              <Skeleton className="m-4 h-[288px]" />
            ) : (
              <DeviceMap
                devices={mappable}
                compact
                fitSignal={mappable.length}
                className="h-[288px] rounded-none border-0"
                ariaLabel="Overview device map"
              />
            )}
          </CardBody>
          <div className="flex flex-wrap items-center justify-between gap-2 border-t border-white/[0.06] px-4 py-2.5">
            <span className="text-[11px] text-slate-500">
              {devices.data
                ? `${mappable.length} of ${devices.data.totalElements} devices have a position`
                : 'Device positions unavailable'}
            </span>
            <span className="text-[10px] text-slate-600">OpenStreetMap tiles · RSSI bands are estimates</span>
          </div>
        </Card>

        <Card className="xl:col-span-2">
          <CardHeader
            title="Recent events"
            description="Newest first, organization-scoped"
            icon={Activity}
            action={
              <div className="flex rounded-lg border border-white/[0.08] bg-navy-950/40 p-0.5">
                {(['proximity', 'location'] as FeedTab[]).map((tab) => (
                  <button
                    key={tab}
                    type="button"
                    onClick={() => setFeedTab(tab)}
                    className={cn(
                      'rounded-md px-2 py-1 text-[10px] font-medium capitalize transition',
                      feedTab === tab ? 'bg-white/[0.08] text-slate-100' : 'text-slate-500 hover:text-slate-300',
                    )}
                    aria-pressed={feedTab === tab}
                  >
                    {tab}
                  </button>
                ))}
              </div>
            }
          />
          <CardBody className="p-2">
            {feed.error ? (
              <ErrorState error={feed.error} onRetry={feed.reload} compact />
            ) : feed.loading && !feed.data ? (
              <div className="space-y-2 p-2">
                {Array.from({ length: 5 }).map((_, index) => (
                  <Skeleton key={index} className="h-12 w-full" />
                ))}
              </div>
            ) : feedTab === 'proximity' ? (
              (proximity.data?.items.length ?? 0) === 0 ? (
                <EmptyState compact icon={RadioTower} title="No proximity events yet" description="Events appear when the Android app reports BLE scans." />
              ) : (
                <div className="space-y-0.5">
                  {proximity.data?.items.map((event) => (
                    <ProximityEventRow key={event.id} event={event} />
                  ))}
                </div>
              )
            ) : (locations.data?.items.length ?? 0) === 0 ? (
              <EmptyState compact icon={MapPin} title="No location events yet" description="GPS fixes reported by phones will list here." />
            ) : (
              <div className="space-y-0.5">
                {locations.data?.items.map((event) => (
                  <LocationEventRow key={event.id} event={event} />
                ))}
              </div>
            )}
          </CardBody>
        </Card>
      </div>

      {/* System strip */}
      <Card>
        <div className="flex flex-wrap items-center gap-x-6 gap-y-3 px-4 py-3">
          <div className="flex items-center gap-2">
            <span className="flex h-7 w-7 items-center justify-center rounded-lg border border-white/[0.08] bg-white/[0.04]">
              <Activity className="h-3.5 w-3.5 text-brand-300" aria-hidden="true" />
            </span>
            <span className="text-xs font-medium text-slate-200">System status</span>
          </div>
          {system.loading && !system.data ? (
            <div className="flex flex-wrap gap-3">
              {Array.from({ length: 4 }).map((_, index) => (
                <Skeleton key={index} className="h-5 w-24" />
              ))}
            </div>
          ) : system.error ? (
            <div className="flex items-center gap-2 text-xs text-rose-300">
              Status unavailable
              <button type="button" onClick={system.reload} className="rounded px-1.5 py-0.5 text-brand-300 hover:bg-white/5">
                Retry
              </button>
            </div>
          ) : (
            <div className="flex flex-wrap items-center gap-x-6 gap-y-2">
              <ServicePill label="API" value={system.data?.status} />
              <ServicePill label="Database" value={system.data?.database} />
              <ServicePill label="Redis" value={system.data?.redis} />
              <span className="text-[11px] text-slate-500">
                WS sessions <span className="font-mono text-slate-300">{system.data?.websocketSessions ?? '—'}</span>
              </span>
              <span className="text-[11px] text-slate-500">
                Uptime <span className="font-mono text-slate-300">{formatUptime(system.data?.uptimeSeconds)}</span>
              </span>
              <span className="text-[11px] text-slate-500">
                Version <span className="font-mono text-slate-300">{system.data?.version ?? '—'}</span>
              </span>
            </div>
          )}
          <div className="ml-auto flex items-center gap-2 text-[10px] text-slate-600">
            {system.data?.status === 'UP' ? (
              <span className="inline-flex items-center gap-1.5 text-emerald-400/80">
                <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-emerald-400" /> Operational
              </span>
            ) : system.error ? (
              <span>Status unavailable</span>
            ) : (
              <span>Checked {formatDateTime(new Date(system.lastUpdatedAt ?? Date.now()))}</span>
            )}
          </div>
        </div>
      </Card>

      <div className="flex flex-wrap items-center justify-between gap-2 text-[10px] text-slate-600">
        <span>
          Fleet statuses refresh every 30 seconds · WS connection state is shown in the top bar
        </span>
        <span>
          {deviceStatusMeta.ONLINE.label} bands classify signal strength, not distance
        </span>
      </div>
    </div>
  );
}
