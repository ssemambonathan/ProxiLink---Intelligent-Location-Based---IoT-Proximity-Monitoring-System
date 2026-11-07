import { useNavigate } from 'react-router-dom';
import { Boxes, Clock3, MapPin, Radio, Rss as RssiIcon, Tag } from 'lucide-react';
import { useApi } from '@/hooks/useApi';
import type { Device, LocationEvent, Page, ProximityEvent } from '@/lib/types';
import { categoryLabel } from '@/lib/meta';
import { formatCoords, formatDateTime, formatDbm, formatMeters, relativeTime } from '@/lib/format';
import { Drawer } from '@/components/ui/Modal';
import { Card, CardHeader } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Chip, ProximityChip, SignalBars, StatusChip } from '@/components/ui/Badge';
import { EmptyState, ErrorState } from '@/components/ui/States';
import { Skeleton } from '@/components/ui/Skeleton';
import { LocationEventRow, ProximityEventRow } from '@/components/EventRows';

interface DeviceDetailsDrawerProps {
  deviceId: string | null;
  onClose: () => void;
  summary?: Device | null;
}

function DetailRow({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex items-start justify-between gap-3 border-b border-white/[0.05] py-2 last:border-0">
      <span className="text-[11px] text-slate-500">{label}</span>
      <span className="max-w-[62%] break-words text-right text-[11px] text-slate-200">{children}</span>
    </div>
  );
}

/** Read-only device inspector used by Devices, Live monitoring and Map. */
export function DeviceDetailsDrawer({ deviceId, onClose, summary }: DeviceDetailsDrawerProps) {
  const navigate = useNavigate();
  const device = useApi<Device>(deviceId ? `/devices/${deviceId}` : null);
  const proximity = useApi<Page<ProximityEvent>>(
    deviceId ? `/events/proximity?deviceId=${encodeURIComponent(deviceId)}&page=0&size=6` : null,
  );
  const locations = useApi<Page<LocationEvent>>(
    deviceId ? `/events/location?deviceId=${encodeURIComponent(deviceId)}&page=0&size=6` : null,
  );

  const data = device.data ?? summary ?? null;
  const metadata = data?.metadata && Object.keys(data.metadata).length > 0 ? data.metadata : null;

  return (
    <Drawer
      open={Boolean(deviceId)}
      onClose={onClose}
      title={data?.name ?? 'Device details'}
      description={
        data
          ? `${categoryLabel(data.category)}${data.deviceType ? ` · ${data.deviceType}` : ''} · ${data.organization ?? '—'}`
          : 'Loading device…'
      }
      widthClass="max-w-lg"
      footer={
        <>
          <Button variant="secondary" size="sm" onClick={onClose}>
            Close
          </Button>
          {deviceId && (
            <Button
              size="sm"
              leftIcon={<MapPin className="h-3.5 w-3.5" />}
              onClick={() => {
                onClose();
                navigate(`/map?device=${encodeURIComponent(deviceId)}`);
              }}
            >
              Show on map
            </Button>
          )}
        </>
      }
    >
      {device.error ? (
        <ErrorState error={device.error} onRetry={device.reload} compact />
      ) : device.loading && !data ? (
        <div className="space-y-3">
          <Skeleton className="h-20 w-full" />
          <Skeleton className="h-32 w-full" />
          <Skeleton className="h-40 w-full" />
        </div>
      ) : !data ? (
        <EmptyState icon={Boxes} title="Device unavailable" description="It may have been deleted or is outside your organization." compact />
      ) : (
        <div className="space-y-4">
          <div className="rounded-xl border border-white/[0.08] bg-white/[0.025] p-3.5">
            <div className="flex flex-wrap items-center gap-1.5">
              <StatusChip status={data.status} />
              <ProximityChip level={data.lastProximity} />
              <Chip tone={data.active ? 'success' : 'neutral'} dot>
                {data.active ? 'Active' : 'Deactivated'}
              </Chip>
              <span className="ml-auto inline-flex items-center gap-1.5 text-[11px] text-slate-400">
                <SignalBars rssi={data.lastRssi} /> {formatDbm(data.lastRssi)}
              </span>
            </div>
            <dl className="mt-2">
              <DetailRow label="Hardware ID">
                <span className="font-mono text-[10px]">{data.hardwareId || '—'}</span>
              </DetailRow>
              <DetailRow label="BLE MAC">
                <span className="font-mono text-[10px]">{data.bleMac || '—'}</span>
              </DetailRow>
              <DetailRow label="Service UUID">
                <span className="break-all font-mono text-[10px]">{data.serviceUuid || '—'}</span>
              </DetailRow>
              <DetailRow label="Owner">{data.ownerName || '—'}</DetailRow>
              <DetailRow label="Registered">{formatDateTime(data.createdAt)}</DetailRow>
              <DetailRow label="Record updated">{relativeTime(data.updatedAt ?? data.lastSeenAt)}</DetailRow>
            </dl>
          </div>

          <div className="grid gap-2 sm:grid-cols-2">
            <div className="rounded-xl border border-white/[0.07] bg-white/[0.02] p-3">
              <p className="flex items-center gap-1.5 text-[10px] uppercase tracking-wider text-slate-500">
                <Clock3 className="h-3 w-3" aria-hidden="true" /> Last communication
              </p>
              <p className="mt-1 text-sm font-medium text-slate-100">{relativeTime(data.lastSeenAt)}</p>
              <p className="text-[10px] text-slate-500">{formatDateTime(data.lastSeenAt)}</p>
            </div>
            <div className="rounded-xl border border-white/[0.07] bg-white/[0.02] p-3">
              <p className="flex items-center gap-1.5 text-[10px] uppercase tracking-wider text-slate-500">
                <MapPin className="h-3 w-3" aria-hidden="true" /> Last position
              </p>
              {data.lastLocation ? (
                <>
                  <p className="mt-1 font-mono text-xs text-slate-100">{formatCoords(data.lastLocation.latitude, data.lastLocation.longitude)}</p>
                  <p className="text-[10px] text-slate-500">
                    ±{data.lastLocation.accuracyM != null ? formatMeters(data.lastLocation.accuracyM) : '—'} · {relativeTime(data.lastLocation.recordedAt)}
                  </p>
                </>
              ) : (
                <p className="mt-1 text-xs text-slate-500">No GPS fix reported yet.</p>
              )}
            </div>
          </div>

          <div className="rounded-xl border border-white/[0.07] bg-white/[0.02] p-3">
            <p className="flex items-center gap-1.5 text-[10px] uppercase tracking-wider text-slate-500">
              <RssiIcon className="h-3 w-3" aria-hidden="true" /> Signal estimate
            </p>
            <p className="mt-1 text-[11px] leading-relaxed text-slate-400">
              {formatDbm(data.lastRssi)} smoothed RSSI → <span className="text-slate-200">{data.lastProximity?.replace(/_/g, ' ') ?? 'unclassified'}</span>.
              Distance estimates are derived from a log-distance path-loss model and are indicative only.
            </p>
          </div>

          <Card>
            <CardHeader title="Recent proximity" icon={Radio} description="Newest first" />
            <div className="p-2">
              {proximity.error ? (
                <ErrorState error={proximity.error} onRetry={proximity.reload} compact />
              ) : proximity.loading && !proximity.data ? (
                <div className="space-y-2 p-2">{Array.from({ length: 3 }).map((_, index) => <Skeleton key={index} className="h-11 w-full" />)}</div>
              ) : (proximity.data?.items.length ?? 0) === 0 ? (
                <EmptyState compact icon={Radio} title="No proximity events" description="BLE scans for this device have not been reported." />
              ) : (
                <div className="space-y-0.5">
                  {proximity.data?.items.map((event) => <ProximityEventRow key={event.id} event={event} />)}
                </div>
              )}
            </div>
          </Card>

          <Card>
            <CardHeader title="Recent locations" icon={MapPin} description="GPS reports linked to this device" />
            <div className="p-2">
              {locations.error ? (
                <ErrorState error={locations.error} onRetry={locations.reload} compact />
              ) : locations.loading && !locations.data ? (
                <div className="space-y-2 p-2">{Array.from({ length: 3 }).map((_, index) => <Skeleton key={index} className="h-11 w-full" />)}</div>
              ) : (locations.data?.items.length ?? 0) === 0 ? (
                <EmptyState compact icon={MapPin} title="No location events" description="No GPS fixes have referenced this device." />
              ) : (
                <div className="space-y-0.5">
                  {locations.data?.items.map((event) => <LocationEventRow key={event.id} event={event} />)}
                </div>
              )}
            </div>
          </Card>

          <div className="rounded-xl border border-white/[0.07] bg-white/[0.02] p-3">
            <p className="flex items-center gap-1.5 text-[10px] uppercase tracking-wider text-slate-500">
              <Tag className="h-3 w-3" aria-hidden="true" /> Metadata
            </p>
            {metadata ? (
              <pre className="mt-1.5 max-h-40 overflow-auto rounded-lg bg-navy-950/60 p-2.5 font-mono text-[10px] leading-relaxed text-slate-400">
                {JSON.stringify(metadata, null, 2)}
              </pre>
            ) : (
              <p className="mt-1 text-[11px] text-slate-500">No metadata attached.</p>
            )}
          </div>
        </div>
      )}
    </Drawer>
  );
}
