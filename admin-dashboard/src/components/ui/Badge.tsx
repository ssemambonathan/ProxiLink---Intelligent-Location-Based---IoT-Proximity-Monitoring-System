import type { ReactNode } from 'react';
import { cn } from '@/lib/utils';
import { proximityLevelMeta, proximityLevels, signalStrength, statusMeta, toneClasses, type Tone } from '@/lib/meta';

export function Chip({
  tone = 'neutral',
  dot = false,
  children,
  className,
}: {
  tone?: Tone;
  dot?: boolean;
  children: ReactNode;
  className?: string;
}) {
  const style = toneClasses[tone];
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 whitespace-nowrap rounded-full border px-2 py-0.5 text-[11px] font-medium leading-4',
        style.chip,
        className,
      )}
    >
      {dot && <span className={cn('h-1.5 w-1.5 rounded-full', style.dot)} aria-hidden="true" />}
      {children}
    </span>
  );
}

export function StatusChip({ status, className }: { status: string | null | undefined; className?: string }) {
  const meta = statusMeta(status);
  return (
    <Chip tone={meta.tone} dot className={className}>
      {meta.label}
    </Chip>
  );
}

export function ProximityChip({ level, className }: { level: string | null | undefined; className?: string }) {
  const meta = proximityLevelMeta(level);
  return (
    <Chip tone={meta.tone} className={className}>
      {meta.label}
    </Chip>
  );
}

export function RoleBadge({ role }: { role: string }) {
  const isAdmin = role.toUpperCase() === 'ADMIN';
  return (
    <Chip tone={isAdmin ? 'brand' : 'neutral'} className="uppercase tracking-wide">
      {role}
    </Chip>
  );
}

export function SignalBars({ rssi, className }: { rssi: number | null | undefined; className?: string }) {
  const strength = signalStrength(rssi);
  const tone = strength >= 3 ? 'bg-emerald-400' : strength === 2 ? 'bg-cyan-400' : strength === 1 ? 'bg-amber-400' : 'bg-slate-600';
  return (
    <span
      className={cn('inline-flex items-end gap-[3px]', className)}
      role="img"
      aria-label={typeof rssi === 'number' ? `Signal ${Math.round(rssi)} dBm` : 'No signal reading'}
      title={typeof rssi === 'number' ? `${Math.round(rssi)} dBm` : 'No signal reading'}
    >
      {[3, 6, 9, 12].map((height, index) => (
        <span
          key={height}
          className={cn('w-[3px] rounded-sm transition-colors', index < strength ? tone : 'bg-white/10')}
          style={{ height: `${height}px` }}
        />
      ))}
    </span>
  );
}

/** Threshold legend shared by map and monitoring views. */
export function ProximityLegend({ className }: { className?: string }) {
  return (
    <div className={cn('flex flex-wrap items-center gap-x-3 gap-y-1.5', className)}>
      {proximityLevels.map((level) => {
        const meta = proximityLevelMeta(level);
        return (
          <span key={level} className="inline-flex items-center gap-1.5 text-[11px] text-slate-400">
            <span className="h-2 w-2 rounded-full" style={{ backgroundColor: meta.hex }} aria-hidden="true" />
            {meta.label}
          </span>
        );
      })}
    </div>
  );
}
