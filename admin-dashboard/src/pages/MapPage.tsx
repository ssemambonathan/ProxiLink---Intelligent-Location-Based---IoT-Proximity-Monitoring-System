import { useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { Crosshair, Filter, MapPin, RefreshCw, Route, Search, X } from 'lucide-react';
import { useApi, useDebouncedValue } from '@/hooks/useApi';
import { useRealtime } from '@/context/RealtimeContext';
import type { Device, LocationEvent, Page } from '@/lib/types';
import { categoryLabel, deviceCategories, deviceStatuses, statusMeta } from '@/lib/meta';
import { formatCoords, relativeTime } from '@/lib/format';
import { cardWrap } from '@/lib/styles';
import { cn, withQuery } from '@/lib/utils';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card, CardBody, CardHeader } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Field, SearchField, Select } from '@/components/ui/Field';
import { Chip, StatusChip } from '@/components/ui/Badge';
import { Toggle } from '@/components/ui/Toggle';
import { Skeleton } from '@/components/ui/Skeleton';
import { EmptyState, ErrorState } from '@/components/ui/States';
import { DeviceMap, MapLegend, toMappableDevices } from '@/components/map/DeviceMap';
import { DeviceDetailsDrawer } from '@/components/DeviceDetailsDrawer';

export function MapPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const { deviceStatuses: liveStatuses } = useRealtime();
  const [q, setQ] = useState('');
  const debouncedQ = useDebouncedValue(q, 350);
  const [status, setStatus] = useState('');
  const [category, setCategory] = useState('');
  const [fitSignal, setFitSignal] = useState(1);
  const [showTrail, setShowTrail] = useState(true);
  const [selectedId, setSelectedId] = useState<string | null>(searchParams.get('device'));
  const [detailId, setDetailId] = useState<string | null>(searchParams.get('device'));

  const devicesPath = withQuery('/devices', {
    page: 0,
    size: 100,
    active: true,
    status: status || undefined,
    category: category || undefined,
    q: debouncedQ || undefined,
  });
  const devices = useApi<Page<Device>>(devicesPath, { pollMs: 60_000 });
  const trail = useApi<Page<LocationEvent>>(
    selectedId && showTrail ? withQuery('/events/location', { deviceId: selectedId, page: 0, size: 100 }) : null,
  );

  useEffect(() => {
    const fromQuery = searchParams.get('device');
    if (fromQuery) {
      setSelectedId(fromQuery);
      setDetailId(fromQuery);
    }
  }, [searchParams]);

  const mergedDevices = useMemo<Device[]>(() => {
    return (devices.data?.items ?? []).map((device) => {
      const update = liveStatuses[device.id];
      if (!update) return device;
      return {
        ...device,
        status: update.status,
        lastRssi: update.lastRssi ?? device.lastRssi,
        lastSeenAt: update.lastSeenAt ?? device.lastSeenAt,
      };
    });
  }, [devices.data, liveStatuses]);

  const mappable = useMemo(() => toMappableDevices(mergedDevices), [mergedDevices]);
  const selectedSummary = mergedDevices.find((device) => device.id === selectedId) ?? null;

  const trailPoints = useMemo<Array<[number, number]>>(() => {
    const items = [...(trail.data?.items ?? [])]
      .filter((event) => typeof event.latitude === 'number' && typeof event.longitude === 'number')
      .sort((a, b) => new Date(a.recordedAt).getTime() - new Date(b.recordedAt).getTime());
    return items.map((event) => [event.latitude, event.longitude]);
  }, [trail.data]);

  const selectDevice = (id: string | null, openDetails = false) => {
    setSelectedId(id);
    if (id && openDetails) setDetailId(id);
    if (openDetails) {
      const next = new URLSearchParams(searchParams);
      if (id) next.set('device', id);
      else next.delete('device');
      setSearchParams(next, { replace: true });
    }
  };

  const filtersActive = Boolean(q || status || category);
  const resetFilters = () => {
    setQ('');
    setStatus('');
    setCategory('');
  };

  const totalMatching = devices.data?.totalElements ?? 0;

  return (
    <div className={cardWrap}>
      <PageHeader
        eyebrow="Monitor"
        title="Interactive map"
        description="OpenStreetMap tiles, status-coloured markers and optional GPS trails. Filter the fleet, fit the view, then inspect a device."
        actions={
          <>
            <Button variant="secondary" size="sm" onClick={devices.reload} loading={devices.refreshing} leftIcon={<RefreshCw className="h-3.5 w-3.5" />}>
              Refresh
            </Button>
            <Link to="/live">
              <Button variant="outline" size="sm" leftIcon={<Crosshair className="h-3.5 w-3.5" />}>
                Live monitoring
              </Button>
            </Link>
          </>
        }
      />

      <div className="flex flex-col gap-4 lg:h-[calc(100vh-11.5rem)] lg:min-h-[560px] lg:flex-row">
        <aside className="flex w-full shrink-0 flex-col gap-3 lg:w-[300px] lg:overflow-y-auto lg:pr-1">
          <Card>
            <CardHeader title="Filters" description="Server-side device query" icon={Filter} />
            <CardBody className="space-y-3">
              <SearchField
                label="Search devices"
                value={q}
                onChange={setQ}
                placeholder="Name, hardware ID, MAC…"
                className="w-full"
              >
                <Search className="h-3.5 w-3.5" aria-hidden="true" />
              </SearchField>
              <div className="grid grid-cols-2 gap-2">
                <Field label="Status" htmlFor="map-status">
                  <Select id="map-status" value={status} onChange={(event) => setStatus(event.target.value)}>
                    <option value="">All statuses</option>
                    {deviceStatuses.map((option) => (
                      <option key={option} value={option}>
                        {statusMeta(option).label}
                      </option>
                    ))}
                  </Select>
                </Field>
                <Field label="Category" htmlFor="map-category">
                  <Select id="map-category" value={category} onChange={(event) => setCategory(event.target.value)}>
                    <option value="">All categories</option>
                    {deviceCategories.map((option) => (
                      <option key={option} value={option}>
                        {categoryLabel(option)}
                      </option>
                    ))}
                  </Select>
                </Field>
              </div>
              <div className="flex items-center justify-between gap-2">
                <p className="text-[11px] text-slate-500">
                  <span className="font-mono text-slate-300">{mappable.length}</span> mapped · {totalMatching} matching
                </p>
                {filtersActive && (
                  <button
                    type="button"
                    onClick={resetFilters}
                    className="inline-flex items-center gap-1 rounded-md px-1.5 py-0.5 text-[11px] text-slate-400 transition hover:bg-white/[0.06] hover:text-slate-200"
                  >
                    <X className="h-3 w-3" /> Clear
                  </button>
                )}
              </div>
              <Button
                variant="secondary"
                size="sm"
                className="w-full"
                onClick={() => setFitSignal((value) => value + 1)}
                leftIcon={<Crosshair className="h-3.5 w-3.5" />}
                disabled={mappable.length === 0}
              >
                Fit to devices
              </Button>
              <div className="flex items-center justify-between gap-2 rounded-lg border border-white/[0.06] bg-white/[0.02] px-2.5 py-2">
                <span className="inline-flex items-center gap-1.5 text-[11px] text-slate-300">
                  <Route className="h-3.5 w-3.5 text-cyan-300" aria-hidden="true" /> Trail for selected
                </span>
                <Toggle
                  checked={showTrail}
                  onChange={setShowTrail}
                  label="Show GPS trail for selected device"
                  disabled={!selectedId}
                />
              </div>
              {selectedId && showTrail && (
                <p className="text-[10px] leading-relaxed text-slate-600">
                  {trail.loading && !trail.data
                    ? 'Loading recent location history…'
                    : trail.error
                      ? 'Trail unavailable — see error on the map panel.'
                      : `${trailPoints.length} GPS point${trailPoints.length === 1 ? '' : 's'} plotted.`}
                </p>
              )}
            </CardBody>
          </Card>

          <Card>
            <CardHeader title="Legend" description="Marker colour = status" icon={MapPin} />
            <CardBody className="space-y-3">
              <MapLegend />
              <div className="border-t border-white/[0.06] pt-3">
                <p className="mb-1.5 text-[10px] uppercase tracking-wider text-slate-500">Glyph = category</p>
                <div className="flex flex-wrap gap-1.5">
                  {deviceCategories.map((option) => (
                    <Chip key={option} tone="neutral">
                      {categoryLabel(option)}
                    </Chip>
                  ))}
                </div>
              </div>
              <p className="text-[10px] leading-relaxed text-slate-600">
                Markers only render for devices with a reported GPS position. RSSI bands are estimates, not metric distances.
              </p>
            </CardBody>
          </Card>

          <Card className="min-h-0 flex-1">
            <CardHeader title="Visible devices" description="Click to inspect" icon={MapPin} action={<span className="text-[10px] text-slate-500">{mergedDevices.length}</span>} />
            <div className="max-h-[260px] overflow-y-auto p-2 lg:max-h-none">
              {devices.loading && !devices.data ? (
                <div className="space-y-2 p-2">{Array.from({ length: 5 }).map((_, index) => <Skeleton key={index} className="h-9 w-full" />)}</div>
              ) : devices.error && !devices.data ? (
                <div className="p-2">
                  <ErrorState error={devices.error} onRetry={devices.reload} compact />
                </div>
              ) : mergedDevices.length === 0 ? (
                <EmptyState compact icon={MapPin} title="No matching devices" description="Adjust filters or register a device." />
              ) : (
                <ul className="space-y-0.5">
                  {mergedDevices.map((device) => {
                    const meta = statusMeta(device.status);
                    const located = Boolean(device.lastLocation);
                    return (
                      <li key={device.id}>
                        <button
                          type="button"
                          onClick={() => selectDevice(device.id, true)}
                          className={cn(
                            'flex w-full items-center gap-2 rounded-lg px-2 py-1.5 text-left transition',
                            selectedId === device.id ? 'bg-brand-500/[0.12]' : 'hover:bg-white/[0.05]',
                          )}
                        >
                          <span className="h-2 w-2 shrink-0 rounded-full" style={{ backgroundColor: meta.hex }} aria-hidden="true" />
                          <span className="min-w-0 flex-1">
                            <span className="block truncate text-[11px] font-medium text-slate-200">{device.name}</span>
                            <span className="block truncate text-[10px] text-slate-500">
                              {located ? formatCoords(device.lastLocation?.latitude, device.lastLocation?.longitude) : 'No GPS position'}
                            </span>
                          </span>
                          <span className="shrink-0 text-[9px] text-slate-600">{located ? relativeTime(device.lastSeenAt) : '—'}</span>
                        </button>
                      </li>
                    );
                  })}
                </ul>
              )}
            </div>
          </Card>
        </aside>

        <div className="min-h-[520px] flex-1 lg:min-h-0">
          {devices.error && !devices.data ? (
            <div className="flex h-full min-h-[320px] items-center justify-center rounded-xl border border-rose-400/20 bg-navy-950/40 p-6">
              <div className="w-full max-w-md">
                <ErrorState error={devices.error} onRetry={devices.reload} />
              </div>
            </div>
          ) : devices.loading && !devices.data ? (
            <Skeleton className="h-full min-h-[320px] w-full rounded-xl" />
          ) : (
            <div className="h-full">
              <DeviceMap
                devices={mappable}
                selectedId={selectedId}
                onSelect={(id) => selectDevice(id, false)}
                onOpenDetails={(id) => selectDevice(id, true)}
                trail={showTrail && trailPoints.length > 1 ? trailPoints : undefined}
                fitSignal={fitSignal}
                className="h-full min-h-[520px]"
                ariaLabel="Interactive device map"
              />
              {trail.error && (
                <div className="mt-2">
                  <ErrorState error={trail.error} onRetry={trail.reload} compact />
                </div>
              )}
            </div>
          )}
        </div>
      </div>

      <div className="flex flex-wrap items-center justify-between gap-2 text-[10px] text-slate-600">
        <span>
          {selectedSummary ? (
            <>Selected <StatusChip status={selectedSummary.status} className="ml-1" /></>
          ) : (
            'Select a marker or list entry to open device details.'
          )}
        </span>
        <span>Tiles © OpenStreetMap contributors · no API keys</span>
      </div>

      <DeviceDetailsDrawer
        deviceId={detailId}
        onClose={() => {
          setDetailId(null);
          if (searchParams.get('device')) {
            const next = new URLSearchParams(searchParams);
            next.delete('device');
            setSearchParams(next, { replace: true });
          }
        }}
        summary={selectedSummary}
      />
    </div>
  );
}
