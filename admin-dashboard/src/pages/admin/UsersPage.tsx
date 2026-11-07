import { useEffect, useState } from 'react';
import { RefreshCw, Search, ShieldCheck, UserCog, Users } from 'lucide-react';
import { useApi, useDebouncedValue } from '@/hooks/useApi';
import { apiRequest, errorMessage } from '@/lib/api';
import type { Page, User } from '@/lib/types';
import { formatDate, relativeTime } from '@/lib/format';
import { cardWrap, tdClass, thClass } from '@/lib/styles';
import { cn, withQuery } from '@/lib/utils';
import { useAuth } from '@/context/AuthContext';
import { useToast } from '@/context/ToastContext';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card, CardHeader } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { SearchField, Select } from '@/components/ui/Field';
import { Chip, RoleBadge } from '@/components/ui/Badge';
import { Toggle } from '@/components/ui/Toggle';
import { TableSkeleton } from '@/components/ui/Skeleton';
import { EmptyState, ErrorState, InlineError } from '@/components/ui/States';
import { Pagination } from '@/components/ui/Pagination';

type PendingKind = 'enabled' | 'roles';

export function UsersPage() {
  const { user: me } = useAuth();
  const toast = useToast();
  const [q, setQ] = useState('');
  const debouncedQ = useDebouncedValue(q, 350);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [pending, setPending] = useState<{ id: string; kind: PendingKind } | null>(null);
  const [rowErrors, setRowErrors] = useState<Record<string, string>>({});

  const users = useApi<Page<User>>(
    withQuery('/users', { q: debouncedQ || undefined, page, size }),
    { pollMs: 120_000 },
  );

  useEffect(() => {
    setPage(0);
  }, [debouncedQ, size]);

  const items = users.data?.items ?? [];

  const applyPatch = async (target: User, body: Record<string, unknown>, kind: PendingKind, success: string) => {
    setPending({ id: target.id, kind });
    setRowErrors((current) => {
      if (!current[target.id]) return current;
      const next = { ...current };
      delete next[target.id];
      return next;
    });
    try {
      await apiRequest<User>(`/users/${target.id}`, { method: 'PATCH', body });
      toast.success(success, target.email);
      users.reload();
    } catch (caught) {
      const message = errorMessage(caught);
      setRowErrors((current) => ({ ...current, [target.id]: message }));
      toast.error('Access change rejected', message);
    } finally {
      setPending(null);
    }
  };

  const roleOf = (target: User): string => {
    const roles = Array.isArray(target.roles) ? target.roles.map((role) => String(role).toUpperCase()) : [];
    return roles.includes('ADMIN') ? 'ADMIN' : 'USER';
  };

  return (
    <div className={cardWrap}>
      <PageHeader
        eyebrow="Administration"
        title="Users"
        description="Enable or disable operator accounts and adjust roles. The backend rejects disabling or demoting your own account."
        actions={
          <>
            <span className="hidden text-[11px] text-slate-500 sm:inline">
              {users.data ? `${users.data.totalElements.toLocaleString()} accounts` : '—'}
            </span>
            <Button variant="secondary" size="sm" onClick={users.reload} loading={users.refreshing} leftIcon={<RefreshCw className="h-3.5 w-3.5" />}>
              Refresh
            </Button>
          </>
        }
      />

      <Card>
        <CardHeader
          title="Operator accounts"
          description="Role changes take effect on the account's next token refresh."
          icon={Users}
          action={
            <div className="w-full sm:w-64">
              <SearchField label="Search users" value={q} onChange={setQ} placeholder="Search name or email…">
                <Search className="h-3.5 w-3.5" aria-hidden="true" />
              </SearchField>
            </div>
          }
        />

        {users.error && !users.data ? (
          <div className="p-4">
            <ErrorState error={users.error} onRetry={users.reload} />
          </div>
        ) : users.loading && !users.data ? (
          <TableSkeleton rows={7} columns={5} />
        ) : items.length === 0 ? (
          <div className="p-4">
            <EmptyState
              icon={Users}
              title={q ? 'No users match this search' : 'No user accounts'}
              description={q ? 'Try a different name or email.' : 'Accounts are created through registration or seeded by the platform.'}
              action={
                q ? (
                  <Button variant="secondary" size="sm" onClick={() => setQ('')}>
                    Clear search
                  </Button>
                ) : undefined
              }
            />
          </div>
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[880px] border-collapse">
                <thead>
                  <tr className="table-head">
                    <th className={thClass} scope="col">Operator</th>
                    <th className={thClass} scope="col">Organization</th>
                    <th className={thClass} scope="col">Roles</th>
                    <th className={thClass} scope="col">Created</th>
                    <th className={thClass} scope="col">Enabled</th>
                    <th className={thClass} scope="col">Access level</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-white/[0.05]">
                  {items.map((user) => {
                    const isSelf = me?.id === user.id;
                    const busy = pending?.id === user.id;
                    const role = roleOf(user);
                    return (
                      <tr key={user.id} className="row-hover">
                        <td className={tdClass}>
                          <div className="flex min-w-0 items-center gap-2">
                            <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-brand-500/80 to-accent-500/80 text-[10px] font-semibold text-navy-950">
                              {user.displayName?.slice(0, 1).toUpperCase() || user.email.slice(0, 1).toUpperCase()}
                            </span>
                            <div className="min-w-0">
                              <p className="flex items-center gap-1.5 truncate text-xs font-medium text-slate-100">
                                {user.displayName || 'Unnamed'}
                                {isSelf && <span className="rounded border border-white/10 bg-white/[0.06] px-1 text-[9px] uppercase tracking-wide text-slate-400">You</span>}
                              </p>
                              <p className="truncate text-[10px] text-slate-500">{user.email}</p>
                            </div>
                          </div>
                        </td>
                        <td className={cn(tdClass, 'text-slate-400')}>{user.organization || '—'}</td>
                        <td className={tdClass}>
                          <div className="flex flex-wrap gap-1">
                            {(Array.isArray(user.roles) ? user.roles : []).map((item) => (
                              <RoleBadge key={String(item)} role={String(item)} />
                            ))}
                          </div>
                        </td>
                        <td className={tdClass} title={user.createdAt}>
                          <span className="text-slate-300">{formatDate(user.createdAt)}</span>
                          <span className="block text-[10px] text-slate-600">{relativeTime(user.createdAt)}</span>
                        </td>
                        <td className={tdClass}>
                          <div className="flex items-center gap-2">
                            <Toggle
                              checked={user.enabled}
                              onChange={(next) =>
                                void applyPatch(user, { enabled: next }, 'enabled', next ? 'Account enabled' : 'Account disabled')
                              }
                              disabled={Boolean(pending)}
                              label={`${user.enabled ? 'Disable' : 'Enable'} ${user.email}`}
                            />
                            <Chip tone={user.enabled ? 'success' : 'danger'} dot>
                              {user.enabled ? 'Enabled' : 'Disabled'}
                            </Chip>
                          </div>
                        </td>
                        <td className={tdClass}>
                          <div className="flex flex-col gap-1">
                            <div className="flex items-center gap-2">
                              <Select
                                aria-label={`Role for ${user.email}`}
                                value={role}
                                disabled={Boolean(pending)}
                                onChange={(event) => {
                                  const nextRole = event.target.value;
                                  if (nextRole === role) return;
                                  void applyPatch(user, { roles: [nextRole] }, 'roles', `Role set to ${nextRole}`);
                                }}
                                className="h-8 w-[104px] text-xs"
                              >
                                <option value="USER">USER</option>
                                <option value="ADMIN">ADMIN</option>
                              </Select>
                              {busy && pending?.kind === 'roles' && <span className="text-[10px] text-slate-500">Saving…</span>}
                              {role === 'ADMIN' && <ShieldCheck className="h-3.5 w-3.5 text-brand-300" aria-hidden="true" />}
                            </div>
                            {rowErrors[user.id] && <InlineError>{rowErrors[user.id]}</InlineError>}
                            {isSelf && <p className="text-[10px] text-slate-600">Self-demotion is rejected by the server.</p>}
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
            <Pagination
              page={users.data?.page ?? page}
              totalPages={users.data?.totalPages ?? 0}
              totalElements={users.data?.totalElements ?? 0}
              size={users.data?.size ?? size}
              onPageChange={setPage}
              onSizeChange={setSize}
              className="border-t border-white/[0.06]"
            />
          </>
        )}
      </Card>

      <div className="flex flex-wrap items-start gap-2 rounded-xl border border-white/[0.06] bg-white/[0.015] px-3.5 py-3 text-[11px] leading-relaxed text-slate-500">
        <UserCog className="mt-0.5 h-3.5 w-3.5 shrink-0 text-slate-500" aria-hidden="true" />
        <p>
          Enabling/disabling and role changes are audited. USER accounts are scoped to their organization and cannot open
          Administration routes; ADMIN accounts see all organizations. The API enforces both even if the UI is bypassed.
        </p>
      </div>
    </div>
  );
}
