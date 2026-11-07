import { useEffect, useRef, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Bell, ChevronDown, LogOut, Menu, Settings2, ShieldCheck, UserRound } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { useNotifications } from '@/context/NotificationsContext';
import { initials } from '@/lib/format';
import { ConnectionPill } from '@/components/ConnectionPill';
import { RoleBadge } from '@/components/ui/Badge';

const pageTitles: Array<[string, string]> = [
  ['/admin/users', 'Users'],
  ['/admin/audit', 'Audit log'],
  ['/admin/settings', 'Settings'],
  ['/devices', 'Devices'],
  ['/analytics', 'Analytics'],
  ['/live', 'Live monitoring'],
  ['/map', 'Interactive map'],
  ['/profile', 'Profile'],
  ['/', 'Overview'],
];

function resolveTitle(pathname: string): string {
  return pageTitles.find(([prefix]) => pathname === prefix || pathname.startsWith(`${prefix}/`))?.[1] ?? 'Console';
}

export function Topbar({ onOpenMobileNav }: { onOpenMobileNav: () => void }) {
  const { user, isAdmin, logout } = useAuth();
  const { unreadCount, openDrawer } = useNotifications();
  const location = useLocation();
  const navigate = useNavigate();
  const [menuOpen, setMenuOpen] = useState(false);
  const [signingOut, setSigningOut] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!menuOpen) return;
    const onPointerDown = (event: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) setMenuOpen(false);
    };
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setMenuOpen(false);
    };
    document.addEventListener('mousedown', onPointerDown);
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('mousedown', onPointerDown);
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [menuOpen]);

  const handleSignOut = async () => {
    setSigningOut(true);
    try {
      await logout();
      navigate('/login', { replace: true });
    } finally {
      setSigningOut(false);
      setMenuOpen(false);
    }
  };

  const title = resolveTitle(location.pathname);
  const unreadLabel = unreadCount > 99 ? '99+' : String(unreadCount);

  return (
    <header className="flex h-14 shrink-0 items-center gap-2 border-b border-white/[0.07] bg-navy-900/80 px-3 backdrop-blur-xl sm:px-5">
      <button
        type="button"
        onClick={onOpenMobileNav}
        className="rounded-lg p-2 text-slate-400 transition hover:bg-white/[0.06] hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-400 lg:hidden"
        aria-label="Open navigation"
      >
        <Menu className="h-[18px] w-[18px]" />
      </button>

      <div className="min-w-0">
        <p className="hidden text-[10px] uppercase tracking-[0.16em] text-slate-600 sm:block">Console</p>
        <h1 className="truncate text-sm font-semibold tracking-tight text-slate-100">{title}</h1>
      </div>

      <div className="ml-auto flex items-center gap-1.5 sm:gap-2">
        <ConnectionPill />

        <button
          type="button"
          onClick={openDrawer}
          className="relative rounded-lg p-2 text-slate-400 transition hover:bg-white/[0.06] hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-400"
          aria-label={unreadCount > 0 ? `Notifications, ${unreadCount} unread` : 'Notifications'}
        >
          <Bell className="h-[18px] w-[18px]" />
          {unreadCount > 0 && (
            <span className="absolute -right-0.5 -top-0.5 flex h-4 min-w-4 items-center justify-center rounded-full border border-navy-900 bg-brand-500 px-1 text-[9px] font-semibold text-white">
              {unreadLabel}
            </span>
          )}
        </button>

        <div className="relative" ref={menuRef}>
          <button
            type="button"
            onClick={() => setMenuOpen((open) => !open)}
            className="flex items-center gap-2 rounded-full border border-white/10 bg-white/[0.04] py-1 pl-1 pr-1.5 transition hover:bg-white/[0.08] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-400 sm:pr-2.5"
            aria-haspopup="menu"
            aria-expanded={menuOpen}
            aria-label="Account menu"
          >
            <span className="flex h-7 w-7 items-center justify-center rounded-full bg-gradient-to-br from-brand-500 to-accent-500 text-[10px] font-semibold text-navy-950">
              {initials(user?.displayName || user?.email)}
            </span>
            <span className="hidden max-w-[120px] truncate text-xs font-medium text-slate-200 sm:block">
              {user?.displayName || user?.email}
            </span>
            <ChevronDown className="hidden h-3.5 w-3.5 text-slate-500 sm:block" aria-hidden="true" />
          </button>

          {menuOpen && (
            <div
              role="menu"
              className="absolute right-0 top-[calc(100%+8px)] z-50 w-64 animate-fade-in-up overflow-hidden rounded-xl border border-white/10 bg-navy-850 shadow-2xl"
            >
              <div className="border-b border-white/[0.07] px-4 py-3">
                <p className="truncate text-sm font-medium text-slate-100">{user?.displayName}</p>
                <p className="truncate text-xs text-slate-500">{user?.email}</p>
                <div className="mt-2 flex flex-wrap items-center gap-1.5">
                  {isAdmin && (
                    <span className="inline-flex items-center gap-1 rounded-full border border-brand-400/30 bg-brand-500/10 px-1.5 py-0.5 text-[10px] font-medium text-brand-300">
                      <ShieldCheck className="h-3 w-3" aria-hidden="true" /> Administrator
                    </span>
                  )}
                  {(Array.isArray(user?.roles) ? user.roles : []).slice(0, 3).map((role) => (
                    <RoleBadge key={String(role)} role={String(role)} />
                  ))}
                </div>
              </div>
              <div className="p-1.5">
                <Link
                  to="/profile"
                  role="menuitem"
                  onClick={() => setMenuOpen(false)}
                  className="flex items-center gap-2.5 rounded-lg px-2.5 py-2 text-[13px] text-slate-300 transition hover:bg-white/[0.06] hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-400"
                >
                  <UserRound className="h-4 w-4 text-slate-500" aria-hidden="true" /> Profile &amp; session
                </Link>
                {isAdmin && (
                  <Link
                    to="/admin/settings"
                    role="menuitem"
                    onClick={() => setMenuOpen(false)}
                    className="flex items-center gap-2.5 rounded-lg px-2.5 py-2 text-[13px] text-slate-300 transition hover:bg-white/[0.06] hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-400"
                  >
                    <Settings2 className="h-4 w-4 text-slate-500" aria-hidden="true" /> Platform settings
                  </Link>
                )}
                <div className="my-1 border-t border-white/[0.07]" />
                <button
                  type="button"
                  role="menuitem"
                  onClick={() => void handleSignOut()}
                  disabled={signingOut}
                  className="flex w-full items-center gap-2.5 rounded-lg px-2.5 py-2 text-left text-[13px] text-slate-300 transition hover:bg-rose-500/10 hover:text-rose-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-400 disabled:opacity-60"
                >
                  <LogOut className="h-4 w-4 text-slate-500" aria-hidden="true" />
                  {signingOut ? 'Signing out…' : 'Sign out'}
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </header>
  );
}
