import type { ReactNode } from 'react';
import type { LucideIcon } from 'lucide-react';
import { cn } from '@/lib/utils';
import { toneClasses, type Tone } from '@/lib/meta';
import { Skeleton } from '@/components/ui/Skeleton';

interface StatCardProps {
  label: string;
  value: number | string | null | undefined;
  icon: LucideIcon;
  tone?: Tone;
  hint?: ReactNode;
  loading?: boolean;
  className?: string;
}

export function StatCard({ label, value, icon: Icon, tone = 'brand', hint, loading, className }: StatCardProps) {
  const style = toneClasses[tone];
  const display =
    typeof value === 'number' ? new Intl.NumberFormat().format(value) : value === null || value === undefined ? '—' : value;

  return (
    <div
      className={cn(
        'group relative overflow-hidden rounded-2xl border border-white/[0.07] bg-gradient-to-b from-white/[0.045] to-white/[0.012] p-4 shadow-card transition-colors hover:border-white/[0.12]',
        className,
      )}
    >
      <div className="flex items-start justify-between gap-2">
        <span
          className={cn(
            'flex h-8 w-8 items-center justify-center rounded-lg border',
            style.chip,
          )}
        >
          <Icon className="h-4 w-4" aria-hidden="true" />
        </span>
        {hint && <span className="max-w-[55%] text-right text-[11px] leading-tight text-slate-500">{hint}</span>}
      </div>
      {loading ? (
        <>
          <Skeleton className="mt-4 h-7 w-16" />
          <Skeleton className="mt-2 h-3 w-24" />
        </>
      ) : (
        <>
          <p className="mt-4 text-2xl font-semibold tracking-tight text-slate-50 tabular-nums">{display}</p>
          <p className="mt-0.5 text-[11px] font-medium uppercase tracking-[0.14em] text-slate-500">{label}</p>
        </>
      )}
    </div>
  );
}
