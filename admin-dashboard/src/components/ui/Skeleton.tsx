import type { CSSProperties } from 'react';
import { cn } from '@/lib/utils';

export function Skeleton({ className, style }: { className?: string; style?: CSSProperties }) {
  return <div className={cn('animate-pulse rounded-md bg-white/[0.065]', className)} style={style} aria-hidden="true" />;
}

export function SkeletonText({ lines = 3, className }: { lines?: number; className?: string }) {
  return (
    <div className={cn('space-y-2', className)} aria-hidden="true">
      {Array.from({ length: lines }).map((_, index) => (
        <Skeleton key={index} className={cn('h-3', index === lines - 1 ? 'w-2/3' : 'w-full')} />
      ))}
    </div>
  );
}

export function StatGridSkeleton({ count = 6, className }: { count?: number; className?: string }) {
  return (
    <div className={cn('grid grid-cols-2 gap-3 sm:gap-4 lg:grid-cols-4', className)} aria-hidden="true">
      {Array.from({ length: count }).map((_, index) => (
        <div key={index} className="rounded-2xl border border-white/[0.06] bg-white/[0.025] p-4">
          <Skeleton className="h-8 w-8 rounded-lg" />
          <Skeleton className="mt-4 h-6 w-16" />
          <Skeleton className="mt-2 h-3 w-24" />
        </div>
      ))}
    </div>
  );
}

export function TableSkeleton({ rows = 6, columns = 5 }: { rows?: number; columns?: number }) {
  return (
    <div className="divide-y divide-white/[0.05]" aria-hidden="true">
      {Array.from({ length: rows }).map((_, row) => (
        <div key={row} className="flex items-center gap-4 px-4 py-3.5">
          {Array.from({ length: columns }).map((__, column) => (
            <Skeleton key={column} className={cn('h-3.5', column === 0 ? 'w-40' : 'flex-1')} />
          ))}
        </div>
      ))}
    </div>
  );
}

export function ChartSkeleton({ height = 280 }: { height?: number }) {
  return (
    <div className="flex items-end gap-2 px-4 pb-4" style={{ height }} aria-hidden="true">
      {[45, 62, 38, 74, 55, 82, 66].map((value, index) => (
        <Skeleton key={index} className="flex-1 rounded-t-md" style={{ height: `${value}%` }} />
      ))}
    </div>
  );
}

export function PageSkeleton() {
  return (
    <div className="space-y-5" aria-label="Loading" role="status">
      <Skeleton className="h-8 w-56" />
      <StatGridSkeleton count={4} />
      <div className="grid gap-4 lg:grid-cols-3">
        <div className="h-72 rounded-2xl border border-white/[0.06] bg-white/[0.02] lg:col-span-2">
          <ChartSkeleton height={280} />
        </div>
        <div className="h-72 rounded-2xl border border-white/[0.06] bg-white/[0.02]" />
      </div>
    </div>
  );
}
