import { Fragment, useEffect, useState } from 'react';
import { ChevronDown, Download, RefreshCw, RotateCcw, ScrollText, ShieldAlert, Search } from 'lucide-react';
import { useApi, useDebouncedValue } from '@/hooks/useApi';
import type { AuditLog, Page } from '@/lib/types';
import { auditCategoryTone } from '@/lib/meta';
import { formatDateTime, relativeTime } from '@/lib/format';
import { cardWrap, tdClass, thClass } from '@/lib/styles';
import { cn, withQuery } from '@/lib/utils';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card, CardBody, CardHeader } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Field, Input, Select } from '@/components/ui/Field';
import { Chip } from '@/components/ui/Badge';
import { TableSkeleton } from '@/components/ui/Skeleton';
import { EmptyState, ErrorState } from '@/components/ui/States';
import { Pagination } from '@/components/ui/Pagination';

const auditCategories = ['AUTH', 'SECURITY', 'DEVICE', 'SETTINGS', 'USER', 'EVENT', 'SYSTEM'];

function startOfDayIso(value: string): string | undefined {
  if (!value) return undefined;
  const date = new Date(`${value}T00:00:00`);
  return Number.isNaN(date.getTime()) ? undefined : date.toISOString();
}

function endOfDayIso(value: string): string | undefined {
  if (!value) return undefined;
  const date = new Date(`${value}T23:59:59.999`);
  return Number.isNaN(date.getTime()) ? undefined : date.toISOString();
}

function DetailField({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="min-w-0">
      <dt className="text-[9px] uppercase tracking-[0.14em] text-slate-600">{label}</dt>
      <dd className="mt-0.5 break-words text-[11px] text-slate-300">{children}</dd>
    </div>
  );
}

export function AuditLogPage() {
  const [action, setAction] = useState('');
  const [category, setCategory] = useState('');
  const [actorId, setActorId] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [expanded, setExpanded] = useState<string | null>(null);

  const debouncedAction = useDebouncedValue(action, 350);
  const debouncedActor = useDebouncedValue(actorId, 350);
  const invalidRange = Boolean(from && to && from > to);

  const path = invalidRange
    ? null
    : withQuery('/audit-logs', {
        action: debouncedAction.trim() || undefined,
        category: category || undefined,
        actorId: debouncedActor.trim() || undefined,
        from: startOfDayIso(from),
        to: endOfDayIso(to),
        page,
        size,
      });
  const logs = useApi<Page<AuditLog>>(path, { pollMs: 120_000 });

  useEffect(() => {
    setPage(0);
    setExpanded(null);
  }, [debouncedAction, category, debouncedActor, from, to, size]);

  const items = logs.data?.items ?? [];
  const filtersActive = Boolean(action || category || actorId || from || to);

  const resetFilters = () => {
    setAction('');
    setCategory('');
    setActorId('');
    setFrom('');
    setTo('');
  };

  return (
    <div className={cardWrap}>
      <PageHeader
        eyebrow="Administration"
        title="Audit log"
        description="Immutable record of security-relevant actions: authentication, device changes, settings updates and user administration."
        actions={
          <>
            <span className="hidden text-[11px] text-slate-500 sm:inline">
              {logs.data ? `${logs.data.totalElements.toLocaleString()} entries` : '—'}
            </span>
            <Button variant="secondary" size="sm" onClick={logs.reload} loading={logs.refreshing} leftIcon={<RefreshCw className="h-3.5 w-3.5" />}>
              Refresh
            </Button>
          </>
        }
      />

      <Card>
        <CardHeader
          title="Filters"
          description="Combine action text, category, actor and a date range."
          icon={ScrollText}
          action={
            <Button variant="ghost" size="sm" onClick={resetFilters} disabled={!filtersActive} leftIcon={<RotateCcw className="h-3.5 w-3.5" />}>
              Reset
            </Button>
          }
        />
        <CardBody className="grid gap-3 border-b border-white/[0.06] sm:grid-cols-2 xl:grid-cols-5">
          <Field label="Action" htmlFor="audit-action" hint="e.g. LOGIN_SUCCESS">
            <Input
              id="audit-action"
              value={action}
              onChange={(event) => setAction(event.target.value)}
              placeholder="Filter by action…"
              className="font-mono text-xs"
            />
          </Field>
          <Field label="Category" htmlFor="audit-category">
            <Select id="audit-category" value={category} onChange={(event) => setCategory(event.target.value)}>
              <option value="">All categories</option>
              {auditCategories.map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="Actor ID" htmlFor="audit-actor" hint="Exact UUID">
            <Input
              id="audit-actor"
              value={actorId}
              onChange={(event) => setActorId(event.target.value)}
              placeholder="uuid…"
              className="font-mono text-xs"
            />
          </Field>
          <Field label="From" htmlFor="audit-from" error={invalidRange ? 'From must be before To.' : undefined}>
            <Input id="audit-from" type="date" value={from} onChange={(event) => setFrom(event.target.value)} max={to || undefined} />
          </Field>
          <Field label="To" htmlFor="audit-to" error={invalidRange ? 'To must be after From.' : undefined}>
            <Input id="audit-to" type="date" value={to} onChange={(event) => setTo(event.target.value)} min={from || undefined} />
          </Field>
        </CardBody>

        {invalidRange ? (
          <div className="p-4">
            <EmptyState icon={Search} title="Invalid date range" description="The From date is after the To date. Adjust the range to query the audit log." />
          </div>
        ) : logs.error && !logs.data ? (
          <div className="p-4">
            <ErrorState error={logs.error} onRetry={logs.reload} />
          </div>
        ) : logs.loading && !logs.data ? (
          <TableSkeleton rows={8} columns={6} />
        ) : items.length === 0 ? (
          <div className="p-4">
            <EmptyState
              icon={ScrollText}
              title={filtersActive ? 'No audit entries match' : 'No audit entries yet'}
              description={
                filtersActive
                  ? 'Widen the date range or clear filters. Audit retention is controlled by the dataRetentionDays setting.'
                  : 'Authentication and administrative actions will be recorded here.'
              }
              action={filtersActive ? <Button variant="secondary" size="sm" onClick={resetFilters}>Clear filters</Button> : undefined}
            />
          </div>
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[920px] border-collapse">
                <thead>
                  <tr className="table-head">
                    <th className={thClass} scope="col">Time</th>
                    <th className={thClass} scope="col">Action</th>
                    <th className={thClass} scope="col">Category</th>
                    <th className={thClass} scope="col">Actor</th>
                    <th className={thClass} scope="col">Entity</th>
                    <th className={thClass} scope="col">IP</th>
                    <th className={cn(thClass, 'w-10 text-right')} scope="col"><span className="sr-only">Details</span></th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-white/[0.05]">
                  {items.map((log) => {
                    const isOpen = expanded === log.id;
                    const tone = auditCategoryTone[log.category] ?? 'neutral';
                    const security = log.category === 'SECURITY';
                    return (
                      <Fragment key={log.id}>
                        <tr
                          className={cn(
                            'row-hover cursor-pointer',
                            security && 'bg-rose-500/[0.03]',
                            isOpen && 'bg-white/[0.035]',
                          )}
                          onClick={() => setExpanded(isOpen ? null : log.id)}
                        >
                          <td className={tdClass}>
                            <span className="text-slate-300">{formatDateTime(log.createdAt)}</span>
                            <span className="block text-[10px] text-slate-600">{relativeTime(log.createdAt)}</span>
                          </td>
                          <td className={tdClass}>
                            <span className="inline-flex items-center gap-1.5">
                              {security && <ShieldAlert className="h-3 w-3 text-rose-300" aria-hidden="true" />}
                              <span className="font-mono text-[11px] text-slate-200">{log.action}</span>
                            </span>
                          </td>
                          <td className={tdClass}>
                            <Chip tone={tone} dot>
                              {log.category}
                            </Chip>
                          </td>
                          <td className={tdClass}>
                            <span className="block max-w-[180px] truncate text-slate-300">{log.actorEmail || log.actorName || '—'}</span>
                            <span className="block max-w-[180px] truncate font-mono text-[10px] text-slate-600">{log.actorId || '—'}</span>
                          </td>
                          <td className={tdClass}>
                            <span className="text-slate-300">{log.entityType || '—'}</span>
                            <span className="block max-w-[160px] truncate font-mono text-[10px] text-slate-600">{log.entityId || ''}</span>
                          </td>
                          <td className={cn(tdClass, 'font-mono text-[10px] text-slate-500')}>{log.ipAddress || '—'}</td>
                          <td className={cn(tdClass, 'text-right')}>
                            <button
                              type="button"
                              aria-expanded={isOpen}
                              aria-label={isOpen ? 'Collapse details' : 'Expand details'}
                              className="rounded-md p-1 text-slate-500 transition hover:bg-white/[0.06] hover:text-slate-200"
                            >
                              <ChevronDown className={cn('h-4 w-4 transition-transform', isOpen && 'rotate-180')} />
                            </button>
                          </td>
                        </tr>
                        {isOpen && (
                          <tr key={`${log.id}-details`} className="bg-navy-950/40">
                            <td colSpan={7} className="px-4 py-3">
                              <div className="rounded-lg border border-white/[0.06] bg-navy-900/60 p-3">
                                <dl className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
                                  <DetailField label="Entry ID"><span className="font-mono text-[10px]">{log.id}</span></DetailField>
                                  <DetailField label="Actor"><span className="font-mono text-[10px]">{log.actorId || '—'}</span></DetailField>
                                  <DetailField label="Entity">
                                    {log.entityType || '—'}
                                    {log.entityId ? <span className="ml-1 font-mono text-[10px] text-slate-500">{log.entityId}</span> : null}
                                  </DetailField>
                                  <DetailField label="IP address"><span className="font-mono text-[10px]">{log.ipAddress || '—'}</span></DetailField>
                                  <DetailField label="Recorded"><span className="font-mono text-[10px]">{formatDateTime(log.createdAt)}</span></DetailField>
                                  <DetailField label="User agent">{log.userAgent || '—'}</DetailField>
                                </dl>
                                {(log.details || log.metadata) && (
                                  <div className="mt-3">
                                    <p className="text-[9px] uppercase tracking-[0.14em] text-slate-600">Context</p>
                                    <pre className="mt-1 max-h-52 overflow-auto rounded-lg bg-navy-950/70 p-2.5 font-mono text-[10px] leading-relaxed text-slate-400">
                                      {JSON.stringify(log.details ?? log.metadata, null, 2)}
                                    </pre>
                                  </div>
                                )}
                              </div>
                            </td>
                          </tr>
                        )}
                      </Fragment>
                    );
                  })}
                </tbody>
              </table>
            </div>
            <Pagination
              page={logs.data?.page ?? page}
              totalPages={logs.data?.totalPages ?? 0}
              totalElements={logs.data?.totalElements ?? 0}
              size={logs.data?.size ?? size}
              onPageChange={setPage}
              onSizeChange={setSize}
              className="border-t border-white/[0.06]"
            />
          </>
        )}
      </Card>

      <div className="flex flex-wrap items-center justify-between gap-2 text-[10px] text-slate-600">
        <span>Click a row to expand identifiers, user agent and JSON context.</span>
        <span className="inline-flex items-center gap-1.5">
          <Download className="h-3 w-3" aria-hidden="true" /> Retention governed by Settings · dataRetentionDays
        </span>
      </div>
    </div>
  );
}
