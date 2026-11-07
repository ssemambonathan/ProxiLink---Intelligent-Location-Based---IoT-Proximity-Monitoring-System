import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Bell, KeyRound, LogOut, MapPin, Server, ShieldCheck, UserRound } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { useNotifications } from '@/context/NotificationsContext';
import { API_BASE_URL } from '@/lib/api';
import { formatDateTime, initials, relativeTime } from '@/lib/format';
import { cardWrap } from '@/lib/styles';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card, CardBody, CardHeader } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Chip, RoleBadge } from '@/components/ui/Badge';

function InfoRow({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex flex-wrap items-start justify-between gap-2 border-b border-white/[0.05] py-2.5 last:border-0">
      <dt className="text-xs text-slate-500">{label}</dt>
      <dd className="max-w-[65%] text-right text-xs text-slate-200">{children}</dd>
    </div>
  );
}

export function ProfilePage() {
  const { user, roles, isAdmin, sessionExpiresAt, logout } = useAuth();
  const { unreadCount, openDrawer } = useNotifications();
  const navigate = useNavigate();
  const [signingOut, setSigningOut] = useState(false);

  const wsEndpoint = import.meta.env.VITE_WS_URL
    ? import.meta.env.VITE_WS_URL
    : `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}/ws`;

  const handleSignOut = async () => {
    setSigningOut(true);
    try {
      await logout();
      navigate('/login', { replace: true });
    } finally {
      setSigningOut(false);
    }
  };

  return (
    <div className={cardWrap}>
      <PageHeader
        eyebrow="Account"
        title="Profile &amp; session"
        description="Your identity, roles and console connection. Role gating is decoded from the access token for navigation; the backend enforces every request."
        actions={
          <Button variant="secondary" onClick={openDrawer} leftIcon={<Bell className="h-4 w-4" />}>
            Notifications{unreadCount > 0 ? ` (${unreadCount})` : ''}
          </Button>
        }
      />

      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader title="Operator identity" icon={UserRound} description="Issued by the ProxiLink auth service" />
          <CardBody>
            <div className="flex items-center gap-4">
              <span className="flex h-16 w-16 items-center justify-center rounded-2xl bg-gradient-to-br from-brand-500 to-accent-500 text-xl font-semibold text-navy-950">
                {initials(user?.displayName || user?.email)}
              </span>
              <div className="min-w-0">
                <h2 className="truncate text-lg font-semibold tracking-tight text-slate-50">{user?.displayName || 'Operator'}</h2>
                <p className="truncate text-sm text-slate-400">{user?.email}</p>
                <div className="mt-2 flex flex-wrap gap-1.5">
                  {isAdmin && (
                    <Chip tone="brand" dot>
                      Administrator
                    </Chip>
                  )}
                  {(Array.isArray(user?.roles) ? user.roles : roles).map((role) => (
                    <RoleBadge key={String(role)} role={String(role)} />
                  ))}
                  <Chip tone={user?.enabled === false ? 'danger' : 'success'} dot>
                    {user?.enabled === false ? 'Disabled' : 'Enabled'}
                  </Chip>
                </div>
              </div>
            </div>

            <dl className="mt-5">
              <InfoRow label="Organization">{user?.organization || '—'}</InfoRow>
              <InfoRow label="Account ID">
                <span className="break-all font-mono text-[10px] text-slate-400">{user?.id ?? '—'}</span>
              </InfoRow>
              <InfoRow label="Member since">{formatDateTime(user?.createdAt)}</InfoRow>
              <InfoRow label="Access level">
                {isAdmin ? 'ADMIN — organization + platform administration' : 'USER — organization-scoped operations'}
              </InfoRow>
            </dl>
          </CardBody>
        </Card>

        <Card>
          <CardHeader title="Session" icon={KeyRound} description="Short-lived access token with rotating refresh" />
          <CardBody>
            <dl>
              <InfoRow label="Access token expires">
                <span className="inline-flex items-center gap-1.5">
                  <span className="h-1.5 w-1.5 rounded-full bg-emerald-400" aria-hidden="true" />
                  {sessionExpiresAt ? relativeTime(sessionExpiresAt) : '—'}
                </span>
                <span className="mt-0.5 block text-[10px] text-slate-500">{formatDateTime(sessionExpiresAt)}</span>
              </InfoRow>
              <InfoRow label="Refresh strategy">Single-flight POST /auth/refresh on 401; tokens rotate</InfoRow>
              <InfoRow label="Storage">
                <span className="font-mono text-[10px]">localStorage</span> (demo — XSS-readable)
              </InfoRow>
              <InfoRow label="Realtime channel">
                <span className="inline-flex items-center gap-1.5 text-emerald-300">
                  <ShieldCheck className="h-3.5 w-3.5" aria-hidden="true" /> JWT handshake, token never logged
                </span>
              </InfoRow>
            </dl>
            <div className="mt-4 flex flex-wrap gap-2">
              <Button
                variant="danger"
                onClick={() => void handleSignOut()}
                loading={signingOut}
                leftIcon={<LogOut className="h-4 w-4" />}
              >
                Sign out
              </Button>
            </div>
            <p className="mt-3 text-[11px] leading-relaxed text-slate-600">
              Signing out revokes the refresh token server-side when reachable, then clears local storage.
            </p>
          </CardBody>
        </Card>

        <Card className="lg:col-span-2">
          <CardHeader title="Connection targets" icon={Server} description="Resolved at runtime — origin-relative by default" />
          <CardBody>
            <dl className="grid gap-x-8 sm:grid-cols-2">
              <InfoRow label="REST base">
                <span className="break-all font-mono text-[10px]">{API_BASE_URL}</span>
              </InfoRow>
              <InfoRow label="WebSocket endpoint">
                <span className="break-all font-mono text-[10px]">{wsEndpoint}</span>
              </InfoRow>
              <InfoRow label="Map tiles">
                <span className="inline-flex items-center gap-1.5">
                  <MapPin className="h-3.5 w-3.5 text-brand-300" aria-hidden="true" /> OpenStreetMap
                </span>
              </InfoRow>
              <InfoRow label="API contract">v1 · paginated JSON · ISO-8601 UTC</InfoRow>
            </dl>
            <p className="mt-3 text-[11px] leading-relaxed text-slate-600">
              The access token is appended to the WebSocket query string only inside the client at connect time; it is
              intentionally omitted from this page and from logs.
            </p>
          </CardBody>
        </Card>
      </div>
    </div>
  );
}
