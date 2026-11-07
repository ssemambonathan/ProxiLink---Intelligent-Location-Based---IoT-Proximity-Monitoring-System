import { useId } from 'react';
import { cn } from '@/lib/utils';

/** ProxiLink mark: a beacon radiating two proximity rings. */
export function BrandMark({ className }: { className?: string }) {
  const gradientId = `brand-${useId().replace(/[:]/g, '')}`;
  return (
    <svg viewBox="0 0 40 40" className={cn('h-9 w-9', className)} role="img" aria-label="ProxiLink">
      <defs>
        <linearGradient id={gradientId} x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stopColor="#5B8CFF" />
          <stop offset="100%" stopColor="#22D3EE" />
        </linearGradient>
      </defs>
      <rect x="0.75" y="0.75" width="38.5" height="38.5" rx="11.5" fill="#0A1020" stroke={`url(#${gradientId})`} strokeOpacity="0.5" strokeWidth="1.5" />
      <circle cx="20" cy="20" r="3.4" fill={`url(#${gradientId})`} />
      <path d="M14.5 25.5a7.8 7.8 0 0 1 0-11M25.5 14.5a7.8 7.8 0 0 1 0 11" stroke={`url(#${gradientId})`} strokeWidth="2" strokeLinecap="round" fill="none" />
      <path d="M10.5 29.5a13.5 13.5 0 0 1 0-19M29.5 10.5a13.5 13.5 0 0 1 0 19" stroke={`url(#${gradientId})`} strokeWidth="1.5" strokeLinecap="round" fill="none" opacity="0.45" />
    </svg>
  );
}
