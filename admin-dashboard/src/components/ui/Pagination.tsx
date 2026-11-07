import { ChevronLeft, ChevronRight, MoreHorizontal } from 'lucide-react';
import { cn, paginationWindow } from '@/lib/utils';
import { Button, IconButton } from '@/components/ui/Button';
import { Select } from '@/components/ui/Field';

interface PaginationProps {
  page: number;
  totalPages: number;
  totalElements: number;
  size: number;
  onPageChange: (page: number) => void;
  onSizeChange?: (size: number) => void;
  sizes?: number[];
  className?: string;
}

export function Pagination({
  page,
  totalPages,
  totalElements,
  size,
  onPageChange,
  onSizeChange,
  sizes = [10, 20, 50, 100],
  className,
}: PaginationProps) {
  const safeTotalPages = Math.max(1, totalPages);
  const from = totalElements === 0 ? 0 : page * size + 1;
  const to = Math.min(totalElements, (page + 1) * size);
  const windowed = paginationWindow(page, safeTotalPages);

  return (
    <div className={cn('flex flex-wrap items-center justify-between gap-3 px-4 py-3', className)}>
      <div className="flex items-center gap-3">
        <p className="text-xs text-slate-500">
          {totalElements === 0 ? 'No results' : `${from}–${to} of ${totalElements.toLocaleString()}`}
        </p>
        {onSizeChange && (
          <label className="flex items-center gap-1.5 text-xs text-slate-500">
            <span className="sr-only">Rows per page</span>
            <Select
              value={String(size)}
              onChange={(event) => onSizeChange(Number(event.target.value))}
              className="h-7 w-[70px] py-0 text-xs"
              aria-label="Rows per page"
            >
              {sizes.map((option) => (
                <option key={option} value={option}>
                  {option} / page
                </option>
              ))}
            </Select>
          </label>
        )}
      </div>
      <div className="flex items-center gap-1">
        <Button
          variant="ghost"
          size="sm"
          disabled={page <= 0}
          onClick={() => onPageChange(page - 1)}
          leftIcon={<ChevronLeft className="h-3.5 w-3.5" aria-hidden="true" />}
        >
          Prev
        </Button>
        <div className="hidden items-center gap-1 sm:flex">
          {windowed.map((entry, index) =>
            entry === 'gap' ? (
              <span key={`gap-${index}`} className="px-1 text-slate-600" aria-hidden="true">
                <MoreHorizontal className="h-4 w-4" />
              </span>
            ) : (
              <button
                key={entry}
                type="button"
                onClick={() => onPageChange(entry)}
                aria-current={entry === page ? 'page' : undefined}
                aria-label={`Page ${entry + 1}`}
                className={cn(
                  'h-8 min-w-8 rounded-lg px-2 text-xs font-medium transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-400',
                  entry === page
                    ? 'border border-brand-400/30 bg-brand-500/15 text-brand-200'
                    : 'text-slate-400 hover:bg-white/[0.06] hover:text-slate-100',
                )}
              >
                {entry + 1}
              </button>
            ),
          )}
        </div>
        <span className="px-2 text-xs text-slate-500 sm:hidden">
          {page + 1} / {safeTotalPages}
        </span>
        <IconButton
          label="Next page"
          onClick={() => onPageChange(page + 1)}
          disabled={page + 1 >= safeTotalPages}
        >
          <ChevronRight className="h-4 w-4" />
        </IconButton>
      </div>
    </div>
  );
}
