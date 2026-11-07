import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { Activity, BarChart3, CalendarRange, Cpu, MapPin, RadioTower, RefreshCw, Wifi } from 'lucide-react';
import {
  Area,
  Bar,
  BarChart,
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
import type { AnalyticsDevices, Overview, TrendsResponse } from '@/lib/types';
import { categoryColor, categoryLabel, proximityLevelMeta, proximityLevels } from '@/lib/meta';
import { formatCompact, relativeTime } from '@/lib/format';
import { cardWrap, thClass } from '@/lib/styles';
import { cn } from '@/lib/utils';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card, CardBody, CardHeader } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { StatCard } from '@/components/ui/StatCard';
import { Skeleton } from '@/components/ui/Skeleton';
import { EmptyState, ErrorState } from '@/components/ui/States';
import { ChartTooltip, chartAxisLine, chartAxisTick, chartGridStroke } from '@/components/charts/ChartTooltip';

type RangeDays = 7 | 14 | 30;

function dayLabel(value: string): string {
  try {
    return format(parseISO(value), 'd MMM');
  } catch {
    return value;
  }
}

export function AnalyticsPage() {
  const [days, setDays] = useState<RangeDays>(7);
  const trends = useApi<TrendsResponse>(`/analytics/trends?days=${days}`, { pollMs: 60_000 });
  const activity = useApi<AnalyticsDevices>(`/analytics/devices?days=${days}`, { pollMs: 60_000 });
  const overview = useApi<Overview>('/dashboard/overview', { pollMs: 60_000 });

  const points = trends.data?.points ?? [];
  const totals = useMemo(() => {
    return points.reduce(
      (acc, point) => ({
        proximity: acc.proximity + point.proximityEvents,
        locations: acc.locations + point.locationEvents,
        peakDevices: Math.max(acc.peakDevices, point.uniqueDevices),
      }),
      { proximity: 0, locations: 0, peakDevices: 0 },
    );
  }, [points]);
  const hasTrend = points.some((point) => point.proximityEvents > 0 || point.locationEvents > 0);

  const distribution = useMemo(() => {
    const counts = new Map((activity.data?.proximityDistribution ?? []).map((item) => [String(item.level), item.count]));
    const ordered = proximityLevels.map((level) => ({ level, count: counts.get(level) ?? 0 }));
    const extras = [...counts.entries()]
      .filter(([level]) => !proximityLevels.includes(level as (typeof proximityLevels)[number]))
      .map(([level, count]) => ({ level, count }));
    return [...ordered, ...extras];
  }, [activity.data]);
  const hasDistribution = distribution.some((entry) => entry.count > 0);

  const onlineRatio = activity.data?.onlineRatio ?? { online: 0, offline: 0 };
  const ratioTotal = onlineRatio.online + onlineRatio.offline;
  const onlinePct = ratioTotal > 0 ? Math.round((onlineRatio.online / ratioTotal) * 100) : null;

  const mostActive = activity.data?.mostActive ?? [];
  const maxActive = mostActive.reduce((max, device) => Math.max(max, device.eventCount), 0);
  const categories = overview.data?.devicesByCategory ?? [];
  const categoryTotal = categories.reduce((sum, entry) => sum + entry.count, 0);

  const refreshAll = () => {
    trends.reload();
    activity.reload();
    overview.reload();
  };

  return (
    <div className={cardWrap}>
      <PageHeader
        eyebrow="Manage"
        title="Analytics"
        description="Event volume, proximity band distribution and the devices that generate the most telemetry over the selected window."
        actions={
          <>
            <div className="flex items-center rounded-lg border border-white/[0.08] bg-navy-950/40 p-0.5" role="group" aria-label="Date range">
              {([7, 14, 30] as RangeDays[]).map((range) => (
                <button
                  key={range}
                  type="button"
                  onClick={() => setDays(range)}
                  aria-pressed={days === range}
                  className={cn(
                    'rounded-md px-2.5 py-1 text-[11px] font-medium transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-400',
                    days === range ? 'bg-white/[0.08] text-slate-100' : 'text-slate-500 hover:text-slate-300',
                  )}
                >
                  {range}d
                </button>
              ))}
            </div>
            <Button variant="secondary" size="sm" onClick={refreshAll} leftIcon={<RefreshCw className="h-3.5 w-3.5" />}>
              Refresh
            </Button>
          </>
        }
      />

      <div className="flex items-center gap-2 text-[11px] text-slate-500">
        <CalendarRange className="h-3.5 w-3.5" aria-hidden="true" />
        Window: last {days} days · Proximity + location events
        {trends.lastUpdatedAt && <span className="ml-auto text-slate-600">Updated {relativeTime(new Date(trends.lastUpdatedAt))}</span>}
      </div>

      <div className="grid grid-cols-2 gap-3 sm:gap-4 lg:grid-cols-4">
        <StatCard
          label="Proximity events"
          value={trends.data ? totals.proximity : null}
          icon={RadioTower}
          tone="brand"
          loading={trends.loading && !trends.data}
          hint={`${days}d total`}
        />
        <StatCard
          label="Location events"
          value={trends.data ? totals.locations : null}
          icon={MapPin}
          tone="info"
          loading={trends.loading && !trends.data}
          hint={`${days}d total`}
        />
        <StatCard
          label="Peak reporting devices"
          value={trends.data ? totals.peakDevices : null}
          icon={Cpu}
          tone="violet"
          loading={trends.loading && !trends.data}
          hint="Unique / day"
        />
        <StatCard
          label="Online ratio"
          value={onlinePct !== null ? `${onlinePct}%` : null}
          icon={Wifi}
          tone="success"
          loading={activity.loading && !activity.data}
          hint={ratioTotal > 0 ? `${onlineRatio.online} / ${ratioTotal}` : 'No fleet data'}
        />
      </div>

      <Card>
        <CardHeader
          title={`Activity trend · ${days} days`}
          description="Proximity vs location event volume with the number of unique reporting devices"
          icon={BarChart3}
          action={
            <span className="hidden items-center gap-3 text-[10px] text-slate-500 sm:flex">
              <span className="inline-flex items-center gap-1.5"><span className="h-1.5 w-1.5 rounded-full bg-brand-400" />Proximity</span>
              <span className="inline-flex items-center gap-1.5"><span className="h-1.5 w-1.5 rounded-full bg-accent-500" />Location</span>
              <span className="inline-flex items-center gap-1.5"><span className="h-1.5 w-1.5 rounded-full bg-violet-400" />Devices</span>
            </span>
          }
        />
        <CardBody>
          {trends.loading && !trends.data ? (
            <Skeleton className="h-[300px] w-full" />
          ) : trends.error ? (
            <ErrorState error={trends.error} onRetry={trends.reload} compact />
          ) : !hasTrend ? (
            <EmptyState
              icon={Activity}
              title="No activity in this window"
              description={`No proximity or location events were recorded in the last ${days} days. Try a wider range or confirm the Android client is syncing.`}
              action={
                <Button variant="secondary" size="sm" onClick={() => setDays(30)}>
                  Switch to 30 days
                </Button>
              }
            />
          ) : (
            <div style={{ height: 316 }}>
              <ResponsiveContainer width="100%" height="100%">
                <ComposedChart data={points} margin={{ top: 8, right: 6, left: -16, bottom: 0 }}>
                  <defs>
                    <linearGradient id="analyticsProximity" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor="#5B8CFF" stopOpacity={0.34} />
                      <stop offset="100%" stopColor="#5B8CFF" stopOpacity={0.02} />
                    </linearGradient>
                    <linearGradient id="analyticsLocation" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor="#22D3EE" stopOpacity={0.26} />
                      <stop offset="100%" stopColor="#22D3EE" stopOpacity={0.02} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid stroke={chartGridStroke} vertical={false} />
                  <XAxis
                    dataKey="date"
                    tick={chartAxisTick}
                    axisLine={chartAxisLine}
                    tickLine={false}
                    minTickGap={days === 7 ? 8 : 26}
                    tickFormatter={dayLabel}
                  />
                  <YAxis yAxisId="events" tick={chartAxisTick} axisLine={false} tickLine={false} width={44} tickFormatter={(value: number) => formatCompact(value)} />
                  <YAxis yAxisId="devices" orientation="right" tick={chartAxisTick} axisLine={false} tickLine={false} width={32} allowDecimals={false} />
                  <Tooltip
                    content={<ChartTooltip labelFormatter={(value) => dayLabel(String(value))} />}
                    cursor={{ stroke: 'rgba(148,163,184,0.25)', strokeDasharray: '3 3' }}
                  />
                  <Area yAxisId="events" type="monotone" dataKey="proximityEvents" name="Proximity events" stroke="#5B8CFF" strokeWidth={2} fill="url(#analyticsProximity)" />
                  <Area yAxisId="events" type="monotone" dataKey="locationEvents" name="Location events" stroke="#22D3EE" strokeWidth={2} fill="url(#analyticsLocation)" />
                  <Line yAxisId="devices" type="monotone" dataKey="uniqueDevices" name="Unique devices" stroke="#A78BFA" strokeWidth={1.75} dot={false} strokeDasharray="5 4" />
                </ComposedChart>
              </ResponsiveContainer>
            </div>
          )}
        </CardBody>
      </Card>

      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader title="Proximity level distribution" description="Server-classified bands over the window" icon={RadioTower} />
          <CardBody>
            {activity.loading && !activity.data ? (
              <Skeleton className="h-[240px] w-full" />
            ) : activity.error ? (
              <ErrorState error={activity.error} onRetry={activity.reload} compact />
            ) : !hasDistribution ? (
              <EmptyState compact icon={RadioTower} title="No proximity samples" description="No proximity events were classified in this range." />
            ) : (
              <div style={{ height: 240 }}>
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart
                    data={distribution.map((entry) => ({ ...entry, label: proximityLevelMeta(entry.level).label }))}
                    layout="vertical"
                    margin={{ top: 4, right: 12, bottom: 0, left: 4 }}
                    barCategoryGap={10}
                  >
                    <CartesianGrid horizontal={false} stroke={chartGridStroke} />
                    <XAxis type="number" tick={chartAxisTick} axisLine={false} tickLine={false} allowDecimals={false} tickFormatter={(value: number) => formatCompact(value)} />
                    <YAxis type="category" dataKey="label" width={90} tick={chartAxisTick} axisLine={false} tickLine={false} />
                    <Tooltip content={<ChartTooltip />} cursor={{ fill: 'rgba(255,255,255,0.03)' }} />
                    <Bar dataKey="count" name="Events" radius={[0, 5, 5, 0]}>
                      {distribution.map((entry) => (
                        <Cell key={entry.level} fill={proximityLevelMeta(entry.level).hex} />
                      ))}
                    </Bar>
                  </BarChart>
                </ResponsiveContainer>
              </div>
            )}
            <p className="mt-2 text-[10px] leading-relaxed text-slate-600">
              Bands derive from smoothed RSSI thresholds; distance cannot be inferred reliably from a single RSSI value.
            </p>
          </CardBody>
        </Card>

        <Card>
          <CardHeader title="Fleet availability" description="Online vs offline device ratio" icon={Wifi} />
          <CardBody>
            {activity.loading && !activity.data ? (
              <Skeleton className="h-[240px] w-full" />
            ) : activity.error ? (
              <ErrorState error={activity.error} onRetry={activity.reload} compact />
            ) : ratioTotal === 0 ? (
              <EmptyState compact icon={Wifi} title="No fleet data" description="Register devices to compute availability." />
            ) : (
              <div className="flex flex-col items-center gap-3 sm:flex-row sm:items-center sm:gap-6">
                <div className="relative" style={{ height: 180, width: 180 }}>
                  <ResponsiveContainer width="100%" height="100%">
                    <PieChart>
                      <Pie data={[{ name: 'Online', value: onlineRatio.online }, { name: 'Offline', value: onlineRatio.offline }]} dataKey="value" innerRadius={58} outerRadius={80} paddingAngle={2} stroke="none">
                        <Cell fill="#34D399" />
                        <Cell fill="#475569" />
                      </Pie>
                      <Tooltip content={<ChartTooltip />} />
                    </PieChart>
                  </ResponsiveContainer>
                  <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
                    <span className="text-2xl font-semibold tracking-tight text-slate-50">{onlinePct ?? '—'}%</span>
                    <span className="text-[10px] uppercase tracking-[0.14em] text-slate-500">online</span>
                  </div>
                </div>
                <div className="w-full space-y-3 sm:flex-1">
                  <div>
                    <div className="flex items-center justify-between text-xs">
                      <span className="inline-flex items-center gap-1.5 text-slate-300"><span className="h-2 w-2 rounded-full bg-emerald-400" />Online</span>
                      <span className="font-mono text-slate-300">{onlineRatio.online}</span>
                    </div>
                    <div className="mt-1 h-1.5 overflow-hidden rounded-full bg-white/[0.06]">
                      <div className="h-full rounded-full bg-emerald-400" style={{ width: `${onlinePct ?? 0}%` }} />
                    </div>
                  </div>
                  <div>
                    <div className="flex items-center justify-between text-xs">
                      <span className="inline-flex items-center gap-1.5 text-slate-300"><span className="h-2 w-2 rounded-full bg-slate-500" />Offline</span>
                      <span className="font-mono text-slate-300">{onlineRatio.offline}</span>
                    </div>
                    <div className="mt-1 h-1.5 overflow-hidden rounded-full bg-white/[0.06]">
                      <div className="h-full rounded-full bg-slate-500" style={{ width: `${onlinePct !== null ? 100 - onlinePct : 0}%` }} />
                    </div>
                  </div>
                  <p className="text-[10px] leading-relaxed text-slate-600">
                    Offline is set by the backend monitor after the configured timeout without communication.
                  </p>
                </div>
              </div>
            )}
          </CardBody>
        </Card>
      </div>

      <div className="grid gap-4 lg:grid-cols-5">
        <Card className="lg:col-span-3">
          <CardHeader title="Most active devices" description={`Event count over ${days} days`} icon={Cpu} />
          <div className="overflow-x-auto">
            {activity.loading && !activity.data ? (
              <div className="space-y-2 p-4">{Array.from({ length: 5 }).map((_, index) => <Skeleton key={index} className="h-9 w-full" />)}</div>
            ) : activity.error ? (
              <div className="p-4">
                <ErrorState error={activity.error} onRetry={activity.reload} compact />
              </div>
            ) : mostActive.length === 0 ? (
              <div className="p-4">
                <EmptyState compact icon={Cpu} title="No active devices" description="No devices produced events in this window." />
              </div>
            ) : (
              <table className="w-full min-w-[420px] border-collapse">
                <thead>
                  <tr>
                    <th className={cn(thClass, 'w-10')} scope="col">#</th>
                    <th className={thClass} scope="col">Device</th>
                    <th className={cn(thClass, 'text-right')} scope="col">Events</th>
                    <th className={cn(thClass, 'w-2/5')} scope="col">Share</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-white/[0.05]">
                  {mostActive.slice(0, 10).map((device, index) => (
                    <tr key={device.deviceId} className="row-hover">
                      <td className="px-4 py-2.5 text-[11px] tabular-nums text-slate-600">{index + 1}</td>
                      <td className="px-4 py-2.5">
                        <Link
                          to={`/map?device=${encodeURIComponent(device.deviceId)}`}
                          className="truncate text-xs font-medium text-slate-200 transition hover:text-brand-300"
                        >
                          {device.name}
                        </Link>
                      </td>
                      <td className="px-4 py-2.5 text-right font-mono text-xs text-slate-300">{device.eventCount.toLocaleString()}</td>
                      <td className="px-4 py-2.5">
                        <div className="h-1.5 w-full overflow-hidden rounded-full bg-white/[0.06]">
                          <div
                            className="h-full rounded-full bg-gradient-to-r from-brand-500 to-accent-500"
                            style={{ width: `${maxActive > 0 ? Math.max(4, Math.round((device.eventCount / maxActive) * 100)) : 0}%` }}
                          />
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </Card>

        <Card className="lg:col-span-2">
          <CardHeader title="Category breakdown" description="Current fleet composition" icon={Cpu} />
          <CardBody>
            {overview.loading && !overview.data ? (
              <div className="space-y-3">{Array.from({ length: 5 }).map((_, index) => <Skeleton key={index} className="h-6 w-full" />)}</div>
            ) : overview.error ? (
              <ErrorState error={overview.error} onRetry={overview.reload} compact />
            ) : categories.length === 0 ? (
              <EmptyState compact icon={Cpu} title="No devices" description="Register a device to see category analytics." />
            ) : (
              <ul className="space-y-3">
                {categories.map((entry) => {
                  const share = categoryTotal > 0 ? (entry.count / categoryTotal) * 100 : 0;
                  return (
                    <li key={String(entry.category)}>
                      <div className="flex items-center justify-between gap-2 text-xs">
                        <span className="inline-flex items-center gap-2 text-slate-300">
                          <span className="h-2 w-2 rounded-full" style={{ backgroundColor: categoryColor(entry.category) }} />
                          {categoryLabel(entry.category)}
                        </span>
                        <span className="font-mono text-slate-400">{entry.count}</span>
                      </div>
                      <div className="mt-1 h-1.5 overflow-hidden rounded-full bg-white/[0.06]">
                        <div className="h-full rounded-full" style={{ width: `${share}%`, backgroundColor: categoryColor(entry.category) }} />
                      </div>
                    </li>
                  );
                })}
              </ul>
            )}
          </CardBody>
        </Card>
      </div>
    </div>
  );
}
