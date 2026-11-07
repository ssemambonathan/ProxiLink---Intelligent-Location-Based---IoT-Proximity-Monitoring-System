import { useEffect, useMemo, useRef } from 'react';
import { CircleMarker, MapContainer, Marker, Polyline, Popup, TileLayer, useMap } from 'react-leaflet';
import * as L from 'leaflet';
import { Maximize2, Minus, Plus } from 'lucide-react';
import type { Device } from '@/lib/types';
import { categoryColor, categoryLabel, statusMeta } from '@/lib/meta';
import { formatCoords, formatDbm, relativeTime } from '@/lib/format';
import { cn } from '@/lib/utils';
import { ProximityChip, SignalBars, StatusChip } from '@/components/ui/Badge';

export interface MappableDevice {
  id: string;
  name: string;
  status: string;
  category: string;
  latitude: number;
  longitude: number;
  rssi?: number | null;
  lastSeenAt?: string | null;
  proximity?: string | null;
  hardwareId?: string | null;
}

export function toMappableDevices(devices: Device[]): MappableDevice[] {
  return devices.flatMap((device) => {
    const location = device.lastLocation;
    if (!location || typeof location.latitude !== 'number' || typeof location.longitude !== 'number') return [];
    if (Number.isNaN(location.latitude) || Number.isNaN(location.longitude)) return [];
    return [
      {
        id: device.id,
        name: device.name,
        status: device.status,
        category: device.category,
        latitude: location.latitude,
        longitude: location.longitude,
        rssi: device.lastRssi,
        lastSeenAt: location.recordedAt ?? device.lastSeenAt,
        proximity: device.lastProximity,
        hardwareId: device.hardwareId,
      },
    ];
  });
}

const glyphs: Record<string, string> = {
  ASSET: '<path d="M12 3 20 7.5v9L12 21 4 16.5v-9L12 3Z"/><path d="M4 7.5 12 12l8-4.5M12 12v9"/>',
  SENSOR: '<circle cx="12" cy="12" r="2"/><path d="M8.5 8.5a5 5 0 0 0 0 7M15.5 8.5a5 5 0 0 1 0 7M5.5 5.5a9 9 0 0 0 0 13M18.5 5.5a9 9 0 0 1 0 13"/>',
  WEARABLE: '<rect x="7" y="6.5" width="10" height="11" rx="3"/><path d="M9.5 6.5V4h5v2.5M9.5 17.5V20h5v-2.5M12 10.5v2l1.5 1"/>',
  INFRASTRUCTURE: '<rect x="4" y="4" width="16" height="16" rx="2"/><path d="M8 8h8M8 12h8M8 16h3"/>',
  GATEWAY: '<rect x="3.5" y="10" width="17" height="7" rx="2"/><path d="M7 13.5h.01M10 13.5h.01M8 10V7a4 4 0 0 1 8 0v3"/>',
  OTHER: '<circle cx="12" cy="12" r="3"/><path d="M12 3v2M12 19v2M3 12h2M19 12h2"/>',
};

const markerIcons = new Map<string, L.DivIcon>();

function markerIcon(status: string, category: string, selected: boolean): L.DivIcon {
  const key = `${status}|${category}|${selected ? 1 : 0}`;
  const cached = markerIcons.get(key);
  if (cached) return cached;
  const meta = statusMeta(status);
  const glyph = glyphs[category] ?? glyphs.OTHER;
  const icon = L.divIcon({
    className: 'proxilink-marker',
    iconSize: [30, 30],
    iconAnchor: [15, 15],
    popupAnchor: [0, -16],
    html: `<div class="pm-dot${selected ? ' pm-dot--selected' : ''}" style="--pm-color:${meta.hex}">
        <svg viewBox="0 0 24 24" fill="none" stroke="rgba(255,255,255,0.92)" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${glyph}</svg>
      </div>`,
  });
  markerIcons.set(key, icon);
  return icon;
}

function FitBounds({
  points,
  fitSignal,
}: {
  points: Array<[number, number]>;
  fitSignal: number;
}) {
  const map = useMap();
  const pointsRef = useRef(points);
  pointsRef.current = points;
  const fittedOnce = useRef(false);
  const lastSignal = useRef(-1);

  useEffect(() => {
    if (pointsRef.current.length === 0) return;
    if (fittedOnce.current && lastSignal.current === fitSignal) return;
    const bounds = L.latLngBounds(pointsRef.current);
    map.fitBounds(bounds, { padding: [44, 44], maxZoom: 16 });
    fittedOnce.current = true;
    lastSignal.current = fitSignal;
  }, [fitSignal, points.length, map]);

  return null;
}

function MapControls({ points }: { points: Array<[number, number]> }) {
  const map = useMap();
  const controlClass =
    'flex h-8 w-8 items-center justify-center rounded-lg border border-white/10 bg-navy-850/90 text-slate-300 shadow-lg backdrop-blur transition hover:bg-navy-750 hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-400';
  return (
    <div className="absolute right-3 top-3 z-[500] flex flex-col gap-1.5">
      <button type="button" className={controlClass} onClick={() => map.zoomIn()} aria-label="Zoom in">
        <Plus className="h-4 w-4" />
      </button>
      <button type="button" className={controlClass} onClick={() => map.zoomOut()} aria-label="Zoom out">
        <Minus className="h-4 w-4" />
      </button>
      <button
        type="button"
        className={controlClass}
        onClick={() => {
          if (points.length > 0) map.fitBounds(L.latLngBounds(points), { padding: [44, 44], maxZoom: 16 });
        }}
        aria-label="Fit map to devices"
        disabled={points.length === 0}
      >
        <Maximize2 className="h-4 w-4" />
      </button>
    </div>
  );
}

interface DeviceMapProps {
  devices: MappableDevice[];
  selectedId?: string | null;
  onSelect?: (id: string) => void;
  onOpenDetails?: (id: string) => void;
  trail?: Array<[number, number]>;
  fitSignal?: number;
  className?: string;
  compact?: boolean;
  ariaLabel?: string;
}

/**
 * Leaflet map with OpenStreetMap tiles only (no API keys, no vendor telemetry).
 * Markers are custom div-icons: colour encodes device status, glyph encodes category.
 */
export function DeviceMap({
  devices,
  selectedId,
  onSelect,
  onOpenDetails,
  trail,
  fitSignal = 0,
  className,
  compact = false,
  ariaLabel = 'Device map',
}: DeviceMapProps) {
  const points = useMemo<Array<[number, number]>>(
    () => devices.map((device) => [device.latitude, device.longitude]),
    [devices],
  );
  const center: [number, number] = points.length > 0 ? points[0] : [12.9716, 77.5946];

  return (
    <div className={cn('relative isolate overflow-hidden rounded-xl border border-white/[0.07] bg-navy-950', className)} aria-label={ariaLabel}>
      <MapContainer
        center={center}
        zoom={devices.length > 0 ? 13 : 4}
        className="h-full w-full"
        zoomControl={false}
        scrollWheelZoom={!compact}
        dragging
        attributionControl
      >
        <TileLayer
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
          maxZoom={19}
        />
        <FitBounds points={points} fitSignal={fitSignal} />
        {!compact && <MapControls points={points} />}
        {trail && trail.length > 1 && (
          <>
            <Polyline positions={trail} pathOptions={{ color: '#22D3EE', weight: 2.5, opacity: 0.75, dashArray: '6 6' }} />
            <CircleMarker center={trail[trail.length - 1]} radius={4} pathOptions={{ color: '#22D3EE', fillColor: '#22D3EE', fillOpacity: 0.9, weight: 2 }} />
          </>
        )}
        {devices.map((device) => (
          <Marker
            key={device.id}
            position={[device.latitude, device.longitude]}
            icon={markerIcon(device.status, device.category, selectedId === device.id)}
            eventHandlers={{
              click: () => onSelect?.(device.id),
            }}
            zIndexOffset={selectedId === device.id ? 500 : 0}
          >
            <Popup minWidth={224} autoPan closeButton>
              <div className="space-y-2">
                <div>
                  <p className="text-[13px] font-semibold text-slate-100">{device.name}</p>
                  <p className="mt-0.5 font-mono text-[10px] text-slate-500">{device.hardwareId || device.id.slice(0, 8)}</p>
                </div>
                <div className="flex flex-wrap items-center gap-1.5">
                  <StatusChip status={device.status} />
                  <ProximityChip level={device.proximity} />
                </div>
                <dl className="grid grid-cols-2 gap-1.5 text-[11px]">
                  <div>
                    <dt className="text-slate-500">Category</dt>
                    <dd className="flex items-center gap-1.5 text-slate-300">
                      <span className="h-1.5 w-1.5 rounded-full" style={{ backgroundColor: categoryColor(device.category) }} />
                      {categoryLabel(device.category)}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-slate-500">Signal</dt>
                    <dd className="flex items-center gap-1.5 text-slate-300">
                      <SignalBars rssi={device.rssi} /> {formatDbm(device.rssi)}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-slate-500">Last seen</dt>
                    <dd className="text-slate-300">{relativeTime(device.lastSeenAt)}</dd>
                  </div>
                  <div>
                    <dt className="text-slate-500">Position</dt>
                    <dd className="font-mono text-[10px] text-slate-300">{formatCoords(device.latitude, device.longitude)}</dd>
                  </div>
                </dl>
                {onOpenDetails && (
                  <button
                    type="button"
                    onClick={() => onOpenDetails(device.id)}
                    className="mt-1 w-full rounded-md border border-brand-400/30 bg-brand-500/15 px-2 py-1.5 text-[11px] font-medium text-brand-200 transition hover:bg-brand-500/25"
                  >
                    Open device details
                  </button>
                )}
              </div>
            </Popup>
          </Marker>
        ))}
      </MapContainer>
      {devices.length === 0 && (
        <div className="pointer-events-none absolute inset-0 z-[400] flex items-center justify-center bg-navy-950/55 p-6">
          <div className="max-w-xs rounded-xl border border-white/10 bg-navy-850/95 px-4 py-3 text-center shadow-xl backdrop-blur">
            <p className="text-sm font-medium text-slate-200">No device locations yet</p>
            <p className="mt-1 text-xs leading-relaxed text-slate-400">
              Markers appear once a phone reports a GPS fix for a registered device.
            </p>
          </div>
        </div>
      )}
    </div>
  );
}

/** Proximity legend colours for map overlays. */
export function MapLegend({ className }: { className?: string }) {
  return (
    <div className={cn('flex flex-wrap gap-x-3 gap-y-1 text-[10px] text-slate-400', className)}>
      {['ONLINE', 'NEARBY', 'OUT_OF_RANGE', 'OFFLINE'].map((status) => {
        const meta = statusMeta(status);
        return (
          <span key={status} className="inline-flex items-center gap-1.5">
            <span className="h-2 w-2 rounded-full" style={{ backgroundColor: meta.hex }} aria-hidden="true" />
            {meta.label}
          </span>
        );
      })}
      <span className="inline-flex items-center gap-1.5">
        <span className="h-2 w-2 rounded-full bg-violet-400" aria-hidden="true" />
        Unregistered
      </span>
    </div>
  );
}
