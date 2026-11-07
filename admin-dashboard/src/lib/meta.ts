import type { DeviceCategory, DeviceStatus, ProximityLevel } from '@/lib/types';

/**
 * Presentation metadata for backend enums. Colors are chosen for contrast on the
 * dark navy surface; `hex` is reused by Leaflet markers and Recharts series.
 */

export type Tone = 'success' | 'info' | 'warning' | 'danger' | 'neutral' | 'violet' | 'brand';

export const toneClasses: Record<Tone, { chip: string; dot: string; text: string; bar: string }> = {
  success: {
    chip: 'border-emerald-400/25 bg-emerald-400/10 text-emerald-300',
    dot: 'bg-emerald-400',
    text: 'text-emerald-300',
    bar: 'bg-emerald-400',
  },
  info: {
    chip: 'border-cyan-400/25 bg-cyan-400/10 text-cyan-300',
    dot: 'bg-cyan-400',
    text: 'text-cyan-300',
    bar: 'bg-cyan-400',
  },
  warning: {
    chip: 'border-amber-400/25 bg-amber-400/10 text-amber-300',
    dot: 'bg-amber-400',
    text: 'text-amber-300',
    bar: 'bg-amber-400',
  },
  danger: {
    chip: 'border-rose-400/25 bg-rose-400/10 text-rose-300',
    dot: 'bg-rose-400',
    text: 'text-rose-300',
    bar: 'bg-rose-400',
  },
  neutral: {
    chip: 'border-slate-400/20 bg-slate-400/10 text-slate-300',
    dot: 'bg-slate-400',
    text: 'text-slate-300',
    bar: 'bg-slate-400',
  },
  violet: {
    chip: 'border-violet-400/25 bg-violet-400/10 text-violet-300',
    dot: 'bg-violet-400',
    text: 'text-violet-300',
    bar: 'bg-violet-400',
  },
  brand: {
    chip: 'border-brand-400/30 bg-brand-500/10 text-brand-300',
    dot: 'bg-brand-400',
    text: 'text-brand-300',
    bar: 'bg-brand-400',
  },
};

export interface StatusMeta {
  label: string;
  tone: Tone;
  hex: string;
}

export const deviceStatusMeta: Record<string, StatusMeta> = {
  ONLINE: { label: 'Online', tone: 'success', hex: '#34D399' },
  NEARBY: { label: 'Nearby', tone: 'info', hex: '#22D3EE' },
  OUT_OF_RANGE: { label: 'Out of range', tone: 'warning', hex: '#FBBF24' },
  OFFLINE: { label: 'Offline', tone: 'neutral', hex: '#94A3B8' },
  UNREGISTERED: { label: 'Unregistered', tone: 'violet', hex: '#A78BFA' },
  INACTIVE: { label: 'Inactive', tone: 'neutral', hex: '#64748B' },
};

export function statusMeta(status: string | null | undefined): StatusMeta {
  if (!status) return { label: 'Unknown', tone: 'neutral', hex: '#64748B' };
  return deviceStatusMeta[status] ?? { label: status.replace(/_/g, ' '), tone: 'neutral', hex: '#64748B' };
}

export const proximityMeta: Record<string, StatusMeta> = {
  IMMEDIATE: { label: 'Immediate', tone: 'danger', hex: '#FB7185' },
  VERY_CLOSE: { label: 'Very close', tone: 'warning', hex: '#FBBF24' },
  NEARBY: { label: 'Nearby', tone: 'info', hex: '#22D3EE' },
  FAR: { label: 'Far', tone: 'neutral', hex: '#94A3B8' },
  OUT_OF_RANGE: { label: 'Out of range', tone: 'neutral', hex: '#64748B' },
};

export function proximityLevelMeta(level: string | null | undefined): StatusMeta {
  if (!level) return { label: 'Unclassified', tone: 'neutral', hex: '#64748B' };
  return proximityMeta[level] ?? { label: level.replace(/_/g, ' '), tone: 'neutral', hex: '#64748B' };
}

export const proximityLevels: ProximityLevel[] = [
  'IMMEDIATE',
  'VERY_CLOSE',
  'NEARBY',
  'FAR',
  'OUT_OF_RANGE',
];

export const deviceStatuses: DeviceStatus[] = [
  'ONLINE',
  'NEARBY',
  'OUT_OF_RANGE',
  'OFFLINE',
  'UNREGISTERED',
  'INACTIVE',
];

export const deviceCategories: DeviceCategory[] = [
  'ASSET',
  'SENSOR',
  'WEARABLE',
  'INFRASTRUCTURE',
  'GATEWAY',
  'OTHER',
];

export const categoryMeta: Record<string, { label: string; hex: string; tone: Tone }> = {
  ASSET: { label: 'Asset', hex: '#5B8CFF', tone: 'brand' },
  SENSOR: { label: 'Sensor', hex: '#22D3EE', tone: 'info' },
  WEARABLE: { label: 'Wearable', hex: '#A78BFA', tone: 'violet' },
  INFRASTRUCTURE: { label: 'Infrastructure', hex: '#34D399', tone: 'success' },
  GATEWAY: { label: 'Gateway', hex: '#FBBF24', tone: 'warning' },
  OTHER: { label: 'Other', hex: '#94A3B8', tone: 'neutral' },
};

export function categoryColor(category: string | null | undefined): string {
  if (!category) return categoryMeta.OTHER.hex;
  return categoryMeta[category]?.hex ?? categoryMeta.OTHER.hex;
}

export function categoryLabel(category: string | null | undefined): string {
  if (!category) return 'Other';
  return categoryMeta[category]?.label ?? category;
}

export const auditCategoryTone: Record<string, Tone> = {
  AUTH: 'brand',
  SECURITY: 'danger',
  DEVICE: 'info',
  SETTINGS: 'warning',
  USER: 'violet',
  EVENT: 'success',
  SYSTEM: 'neutral',
};

export function notificationTone(type: string | null | undefined): Tone {
  if (!type) return 'neutral';
  if (type.includes('ALERT') || type.includes('PROXIMITY')) return 'warning';
  if (type.includes('DEVICE')) return 'info';
  if (type.includes('SYSTEM')) return 'violet';
  return 'neutral';
}

/** RSSI to a 0–4 bar strength. Bands are indicative, not linear. */
export function signalStrength(rssi: number | null | undefined): number {
  if (typeof rssi !== 'number') return 0;
  if (rssi >= -55) return 4;
  if (rssi >= -65) return 3;
  if (rssi >= -75) return 2;
  if (rssi >= -85) return 1;
  return 0;
}
