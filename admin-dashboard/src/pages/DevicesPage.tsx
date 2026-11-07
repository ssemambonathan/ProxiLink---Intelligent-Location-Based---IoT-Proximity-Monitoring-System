import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Boxes, Cpu, Pencil, Plus, Power, PowerOff, RefreshCw, RotateCcw, Search, Trash2 } from 'lucide-react';
import { useApi, useDebouncedValue } from '@/hooks/useApi';
import { apiRequest, errorMessage } from '@/lib/api';
import type { Device, Page } from '@/lib/types';
import { categoryColor, categoryLabel, deviceCategories, deviceStatuses, statusMeta } from '@/lib/meta';
import { formatDateTime, formatDbm, relativeTime } from '@/lib/format';
import { cardWrap, tdClass, thClass } from '@/lib/styles';
import { cn, withQuery } from '@/lib/utils';
import { useToast } from '@/context/ToastContext';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card, CardBody, CardHeader } from '@/components/ui/Card';
import { Button, IconButton } from '@/components/ui/Button';
import { Field, SearchField, Select } from '@/components/ui/Field';
import { Chip, SignalBars, StatusChip } from '@/components/ui/Badge';
import { TableSkeleton } from '@/components/ui/Skeleton';
import { EmptyState, ErrorState } from '@/components/ui/States';
import { Pagination } from '@/components/ui/Pagination';
import { ConfirmDialog } from '@/components/ui/Modal';
import { DeviceFormModal } from '@/components/DeviceFormModal';
import { DeviceDetailsDrawer } from '@/components/DeviceDetailsDrawer';

type DeviceAction = 'activate' | 'deactivate' | 'delete';

const actionCopy: Record<DeviceAction, { title: (name: string) => string; description: string; confirm: string; success: string }> = {
  activate: {
    title: (name) => `Activate ${name}?`,
    description: 'The device becomes eligible for event correlation again. Its derived status returns on the next report.',
    confirm: 'Activate',
    success: 'Device activated',
  },
  deactivate: {
    title: (name) => `Deactivate ${name}?`,
    description: 'Existing events are retained, but the device stops updating its status and is hidden from the default active list.',
    confirm: 'Deactivate',
    success: 'Device deactivated',
  },
  delete: {
    title: (name) => `Delete ${name}?`,
    description:
      'This is a soft delete: the device is marked inactive (status INACTIVE). Historical proximity and location events remain linked for audit.',
    confirm: 'Delete device',
    success: 'Device deleted',
  },
};

export function DevicesPage() {
  const toast = useToast();
  const [q, setQ] = useState('');
  const debouncedQ = useDebouncedValue(q, 350);
  const [status, setStatus] = useState('');
  const [category, setCategory] = useState('');
  const [activeFilter, setActiveFilter] = useState('true');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<Device | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [confirmTarget, setConfirmTarget] = useState<{ action: DeviceAction; device: Device } | null>(null);
  const [pendingId, setPendingId] = useState<string | null>(null);

  const path = withQuery('/devices', {
    page,
    size,
    q: debouncedQ || undefined,
    status: status || undefined,
    category: category || undefined,
    active: activeFilter === '' ? undefined : activeFilter,
  });
  const devices = useApi<Page<Device>>(path, { pollMs: 60_000 });

  useEffect(() => {
    setPage(0);
  }, [debouncedQ, status, category, activeFilter, size]);

  useEffect(() => {
    if (!devices.data) return;
    if (devices.data.totalPages > 0 && page >= devices.data.totalPages) {
      setPage(devices.data.totalPages - 1);
    }
  }, [devices.data, page]);

  const items = devices.data?.items ?? [];
  const filtersActive = Boolean(q || status || category || activeFilter !== 'true');

  const resetFilters = () => {
    setQ('');
    setStatus('');
    setCategory('');
    setActiveFilter('true');
  };

  const openCreate = () => {
    setEditing(null);
    setFormOpen(true);
  };

  const openEdit = (device: Device) => {
    setEditing(device);
    setFormOpen(true);
  };

  const runAction = async (action: DeviceAction, device: Device) => {
    setPendingId(device.id);
    try {
      if (action === 'delete') {
        await apiRequest<void>(`/devices/${device.id}`, { method: 'DELETE' });
      } else {
        await apiRequest<Device>(`/devices/${device.id}/${action}`, { method: 'POST' });
      }
      toast.success(actionCopy[action].success, device.name);
      devices.reload();
    } catch (caught) {
      toast.error('Device action failed', errorMessage(caught));
    } finally {
      setPendingId(null);
      setConfirmTarget(null);
    }
  };

  const summary = items.find((device) => device.id === selectedId) ?? null;
  const pendingCopy = confirmTarget ? actionCopy[confirmTarget.action] : null;

  return (
    <div className={cardWrap}>
      <PageHeader
        eyebrow="Manage"
        title="Devices"
        description="Register beacons and sensors, adjust identity metadata, and control availability. Status is always derived from live telemetry."
        actions={
          <>
            <span className="hidden text-[11px] text-slate-500 sm:inline">
              {devices.lastUpdatedAt ? `Updated ${relativeTime(new Date(devices.lastUpdatedAt))}` : 'Awaiting data'}
            </span>
            <Button variant="secondary" size="sm" onClick={devices.reload} loading={devices.refreshing} leftIcon={<RefreshCw className="h-3.5 w-3.5" />}>
              Refresh
            </Button>
            <Button size="sm" onClick={openCreate} leftIcon={<Plus className="h-4 w-4" />}>
              Register device
            </Button>
          </>
        }
      />

      <Card>
        <CardHeader
          title="Fleet"
          description="Search by name, hardware ID or MAC. Pagination and filters are applied server-side."
          icon={Boxes}
          action={
            <span className="text-[10px] text-slate-500">
              {devices.data ? `${devices.data.totalElements.toLocaleString()} total` : '—'}
            </span>
          }
        />
        <CardBody className="border-b border-white/[0.06] py-3">
          <div className="flex flex-col gap-3 xl:flex-row xl:items-end">
            <div className="w-full xl:max-w-xs">
              <SearchField label="Search devices" value={q} onChange={setQ} placeholder="Search name, hardware ID, MAC…">
                <Search className="h-3.5 w-3.5" aria-hidden="true" />
              </SearchField>
            </div>
            <div className="grid flex-1 grid-cols-2 gap-2 sm:grid-cols-3">
              <Field label="Status" htmlFor="devices-status">
                <Select id="devices-status" value={status} onChange={(event) => setStatus(event.target.value)}>
                  <option value="">All statuses</option>
                  {deviceStatuses.map((option) => (
                    <option key={option} value={option}>
                      {statusMeta(option).label}
                    </option>
                  ))}
                </Select>
              </Field>
              <Field label="Category" htmlFor="devices-category">
                <Select id="devices-category" value={category} onChange={(event) => setCategory(event.target.value)}>
                  <option value="">All categories</option>
                  {deviceCategories.map((option) => (
                    <option key={option} value={option}>
                      {categoryLabel(option)}
                    </option>
                  ))}
                </Select>
              </Field>
              <Field label="Availability" htmlFor="devices-active">
                <Select id="devices-active" value={activeFilter} onChange={(event) => setActiveFilter(event.target.value)}>
                  <option value="true">Active only</option>
                  <option value="false">Inactive only</option>
                  <option value="">All devices</option>
                </Select>
              </Field>
            </div>
            <div className="flex items-center gap-2 xl:pb-0.5">
              <Button
                variant="ghost"
                size="sm"
                onClick={resetFilters}
                disabled={!filtersActive}
                leftIcon={<RotateCcw className="h-3.5 w-3.5" />}
              >
                Reset
              </Button>
            </div>
          </div>
        </CardBody>

        {devices.error && !devices.data ? (
          <div className="p-4">
            <ErrorState error={devices.error} onRetry={devices.reload} />
          </div>
        ) : devices.loading && !devices.data ? (
          <TableSkeleton rows={8} columns={6} />
        ) : items.length === 0 ? (
          <div className="p-4">
            <EmptyState
              icon={Cpu}
              title={filtersActive ? 'No devices match these filters' : 'No devices registered yet'}
              description={
                filtersActive
                  ? 'Try clearing the status, category or search filters.'
                  : 'Register a beacon or sensor to start correlating BLE proximity and GPS positions.'
              }
              action={
                filtersActive ? (
                  <Button variant="secondary" size="sm" onClick={resetFilters}>
                    Clear filters
                  </Button>
                ) : (
                  <Button size="sm" onClick={openCreate} leftIcon={<Plus className="h-4 w-4" />}>
                    Register device
                  </Button>
                )
              }
            />
          </div>
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[900px] border-collapse">
                <thead>
                  <tr className="table-head">
                    <th className={thClass} scope="col">Device</th>
                    <th className={thClass} scope="col">Category</th>
                    <th className={thClass} scope="col">Status</th>
                    <th className={thClass} scope="col">Signal</th>
                    <th className={thClass} scope="col">Owner</th>
                    <th className={thClass} scope="col">Last seen</th>
                    <th className={thClass} scope="col">Availability</th>
                    <th className={cn(thClass, 'text-right')} scope="col">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-white/[0.05]">
                  {items.map((device) => {
                    const busy = pendingId === device.id;
                    return (
                      <tr
                        key={device.id}
                        className={cn('row-hover cursor-pointer', !device.active && 'opacity-70')}
                        onClick={() => setSelectedId(device.id)}
                      >
                        <td className={tdClass}>
                          <button
                            type="button"
                            onClick={(event) => {
                              event.stopPropagation();
                              setSelectedId(device.id);
                            }}
                            className="min-w-0 text-left focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-400"
                          >
                            <span className="block max-w-[240px] truncate text-xs font-medium text-slate-100">{device.name}</span>
                            <span className="mt-0.5 block max-w-[240px] truncate font-mono text-[10px] text-slate-500">
                              {device.hardwareId || device.bleMac || device.id.slice(0, 8)}
                            </span>
                          </button>
                        </td>
                        <td className={tdClass}>
                          <span className="inline-flex items-center gap-1.5 text-slate-300">
                            <span className="h-1.5 w-1.5 rounded-full" style={{ backgroundColor: categoryColor(device.category) }} aria-hidden="true" />
                            {categoryLabel(device.category)}
                          </span>
                        </td>
                        <td className={tdClass}><StatusChip status={device.status} /></td>
                        <td className={tdClass}>
                          <span className="inline-flex items-center gap-2">
                            <SignalBars rssi={device.lastRssi} />
                            <span className="font-mono text-[11px] text-slate-400">{formatDbm(device.lastRssi)}</span>
                          </span>
                        </td>
                        <td className={cn(tdClass, 'text-slate-400')}>{device.ownerName || '—'}</td>
                        <td className={tdClass} title={formatDateTime(device.lastSeenAt)}>
                          <span className="text-slate-300">{relativeTime(device.lastSeenAt)}</span>
                          <span className="block text-[10px] text-slate-600">{formatDateTime(device.lastSeenAt)}</span>
                        </td>
                        <td className={tdClass}>
                          <Chip tone={device.active ? 'success' : 'neutral'} dot>
                            {device.active ? 'Active' : 'Inactive'}
                          </Chip>
                        </td>
                        <td className={cn(tdClass, 'text-right')}>
                          <div className="flex items-center justify-end gap-0.5" onClick={(event) => event.stopPropagation()}>
                            <IconButton label={`Edit ${device.name}`} onClick={() => openEdit(device)} disabled={busy}>
                              <Pencil className="h-3.5 w-3.5" />
                            </IconButton>
                            <IconButton
                              label={device.active ? `Deactivate ${device.name}` : `Activate ${device.name}`}
                              onClick={() => setConfirmTarget({ action: device.active ? 'deactivate' : 'activate', device })}
                              disabled={busy}
                            >
                              {device.active ? <PowerOff className="h-3.5 w-3.5" /> : <Power className="h-3.5 w-3.5" />}
                            </IconButton>
                            <IconButton
                              label={`Delete ${device.name}`}
                              onClick={() => setConfirmTarget({ action: 'delete', device })}
                              disabled={busy}
                              className="hover:bg-rose-500/10 hover:text-rose-300"
                            >
                              <Trash2 className="h-3.5 w-3.5" />
                            </IconButton>
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
            <Pagination
              page={devices.data?.page ?? page}
              totalPages={devices.data?.totalPages ?? 0}
              totalElements={devices.data?.totalElements ?? 0}
              size={devices.data?.size ?? size}
              onPageChange={setPage}
              onSizeChange={setSize}
              className="border-t border-white/[0.06]"
            />
          </>
        )}
      </Card>

      {devices.error && devices.data && <ErrorState error={devices.error} onRetry={devices.reload} compact />}

      <div className="flex flex-wrap items-center justify-between gap-2 text-[10px] text-slate-600">
        <span>Deleting a device is a soft delete (active=false, status INACTIVE) — history is preserved.</span>
        <span>
          Need live signal? <Link to="/live" className="text-brand-300 hover:text-brand-200">Open live monitoring</Link>
        </span>
      </div>

      <DeviceFormModal
        open={formOpen}
        device={editing}
        onClose={() => setFormOpen(false)}
        onSaved={() => devices.reload()}
      />

      <ConfirmDialog
        open={Boolean(confirmTarget)}
        onClose={() => setConfirmTarget(null)}
        onConfirm={() => {
          if (!confirmTarget) return;
          return runAction(confirmTarget.action, confirmTarget.device);
        }}
        title={confirmTarget ? actionCopy[confirmTarget.action].title(confirmTarget.device.name) : ''}
        description={pendingCopy?.description ?? ''}
        confirmLabel={pendingCopy?.confirm ?? 'Confirm'}
        destructive={confirmTarget?.action === 'delete'}
      />

      <DeviceDetailsDrawer deviceId={selectedId} onClose={() => setSelectedId(null)} summary={summary} />
    </div>
  );
}
