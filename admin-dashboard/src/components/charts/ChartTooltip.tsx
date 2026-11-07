import type { ReactNode } from 'react';

export const chartGridStroke = 'rgba(148, 163, 184, 0.10)';
export const chartAxisTick = { fill: '#64748B', fontSize: 11 };
export const chartAxisLine = { stroke: 'rgba(148, 163, 184, 0.14)' };

interface TooltipPayloadItem {
  name?: string | number;
  value?: number | string | null;
  color?: string;
  dataKey?: string | number;
  payload?: Record<string, unknown>;
}

interface ChartTooltipProps {
  active?: boolean;
  payload?: TooltipPayloadItem[];
  label?: string | number;
  labelFormatter?: (label: string | number) => ReactNode;
  valueFormatter?: (value: number | string | null | undefined, item: TooltipPayloadItem) => ReactNode;
}

/** Dark glass tooltip shared by every Recharts surface. */
export function ChartTooltip({ active, payload, label, labelFormatter, valueFormatter }: ChartTooltipProps) {
  if (!active || !payload || payload.length === 0) return null;
  return (
    <div className="rounded-lg border border-white/10 bg-navy-800/95 px-3 py-2 shadow-xl backdrop-blur">
      {label !== undefined && (
        <p className="mb-1.5 text-[11px] font-medium text-slate-300">
          {labelFormatter ? labelFormatter(label) : label}
        </p>
      )}
      <div className="space-y-1">
        {payload.map((item, index) => (
          <div key={`${item.dataKey ?? index}`} className="flex items-center justify-between gap-4 text-[11px]">
            <span className="inline-flex items-center gap-1.5 text-slate-400">
              <span className="h-1.5 w-1.5 rounded-full" style={{ backgroundColor: item.color ?? '#5B8CFF' }} />
              {item.name}
            </span>
            <span className="font-mono text-slate-100">
              {valueFormatter
                ? valueFormatter(item.value, item)
                : typeof item.value === 'number'
                  ? item.value.toLocaleString()
                  : item.value ?? '—'}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}
