import { Link, NavLink } from 'react-router-dom';
import {
  BarChart3,
  ChevronsLeft,
  ChevronsRight,
  Cpu,
  LayoutDashboard,
  LogOut,
  Map,
  Radio,
  ScrollText,
  Settings2,
  Users,
  X,
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { BrandMark } from '@/components/BrandMark';
import { useAuth } from '@/context/AuthContext';
import { initials } from '@/lib/format';
import { cn } from '@/lib/utils';

interface NavItem {
  to: string;
  label: string;
  icon: LucideIcon;
  end?: boolean;
}

const monitorItems: NavItem[] = [
  { to: '/', label: 'Overview', icon: LayoutDashboard, end: true },
  { to: '/live', label: 'Live monitoring', icon: Radio },
  { to: '/map', label: 'Map', icon: Map },
];

const manageItems: NavItem[] = [
  { to: '/devices', label: 'Devices', icon: Cpu },
  { to: '/analytics', label: 'Analytics', icon: BarChart3 },
];

const adminItems: NavItem[] = [
  { to: '/admin/users', label: 'Users', icon: Users },
  { to: '/admin/audit', label: 'Audit log', icon: ScrollText },
  { to: '/admin/settings', label: 'Settings', icon: Settings2 },
];

interface SidebarProps {
  isAdmin: boolean;
  collapsed: boolean;
  onToggleCollapsed: () => void;
  mobileOpen: boolean;
  onCloseMobile: () => void;
}

export function Sidebar({ isAdmin, collapsed, onToggleCollapsed, mobileOpen, onCloseMobile }: SidebarProps) {
  const { user, logout } = useAuth();
  const org = user?.organization || 'No organization';

  const renderItems = (items: NavItem[]) =>
    items.map((item) => (
      <NavLink
        key={item.to}
        to={item.to}
        end={item.end}
        onClick={onCloseMobile}
        title={collapsed ? item.label : undefined}
        className={({ isActive }) =>
          cn(
            'group relative flex items-center gap-2.5 rounded-lg px-2.5 py-2 text-[13px] font-medium transition-colors',
            'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-400/70',
            isActive
              ? 'bg-brand-500/[0.12] text-white'
              : 'text-slate-400 hover:bg-white/[0.05] hover:text-slate-100',
          )
        }
      >
        {({ isActive }) => (
          <>
            <span
              className={cn(
                'absolute inset-y-1.5 left-0 w-[2px] rounded-full bg-brand-400 transition-opacity',
                isActive ? 'opacity-100' : 'opacity-0',
              )}
              aria-hidden="true"
            />
            <item.icon
              className={cn('h-[17px] w-[17px] shrink-0 transition-colors', isActive ? 'text-brand-300' : 'text-slate-500 group-hover:text-slate-300')}
              aria-hidden="true"
            />
            <span className={cn('truncate', collapsed && 'lg:hidden')}>{item.label}</span>
          </>
        )}
      </NavLink>
    ));

  return (
    <aside
      className={cn(
        'fixed inset-y-0 left-0 z-50 flex w-[264px] flex-col border-r border-white/[0.07] bg-navy-900/95 backdrop-blur-xl transition-[width,transform] duration-200 lg:static lg:z-auto lg:translate-x-0',
        collapsed ? 'lg:w-[72px]' : 'lg:w-[248px]',
        mobileOpen ? 'translate-x-0' : '-translate-x-full',
      )}
      aria-label="Primary navigation"
    >
      <div className={cn('flex h-14 items-center gap-2.5 border-b border-white/[0.07] px-3.5', collapsed && 'lg:justify-center lg:px-2')}>
        <Link to="/" className="flex min-w-0 items-center gap-2.5 rounded-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-400" onClick={onCloseMobile}>
          <BrandMark className="h-8 w-8 shrink-0" />
          <span className={cn('min-w-0', collapsed && 'lg:hidden')}>
            <span className="block truncate text-sm font-semibold tracking-tight text-slate-50">ProxiLink</span>
            <span className="block text-[10px] uppercase tracking-[0.18em] text-slate-500">Console</span>
          </span>
        </Link>
        <button
          type="button"
          onClick={onCloseMobile}
          className="ml-auto rounded-lg p-1.5 text-slate-400 hover:bg-white/[0.06] hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-400 lg:hidden"
          aria-label="Close navigation"
        >
          <X className="h-4 w-4" />
        </button>
        <button
          type="button"
          onClick={onToggleCollapsed}
          className={cn(
            'ml-auto hidden rounded-lg p-1.5 text-slate-500 transition hover:bg-white/[0.06] hover:text-slate-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-400 lg:block',
            collapsed && 'lg:ml-0 lg:absolute lg:-right-3 lg:top-[18px] lg:z-10 lg:rounded-full lg:border lg:border-white/10 lg:bg-navy-800',
          )}
          aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
          aria-pressed={collapsed}
        >
          {collapsed ? <ChevronsRight className="h-4 w-4" /> : <ChevronsLeft className="h-4 w-4" />}
        </button>
      </div>

      <nav className="flex-1 space-y-5 overflow-y-auto px-2.5 py-4">
        <div>
          <p className={cn('mb-1.5 px-2.5 text-[10px] font-semibold uppercase tracking-[0.16em] text-slate-600', collapsed && 'lg:hidden')}>
            Monitor
          </p>
          <div className="space-y-0.5">{renderItems(monitorItems)}</div>
        </div>
        <div>
          <p className={cn('mb-1.5 px-2.5 text-[10px] font-semibold uppercase tracking-[0.16em] text-slate-600', collapsed && 'lg:hidden')}>
            Manage
          </p>
          <div className="space-y-0.5">{renderItems(manageItems)}</div>
        </div>
        {isAdmin && (
          <div>
            <p className={cn('mb-1.5 flex items-center gap-1.5 px-2.5 text-[10px] font-semibold uppercase tracking-[0.16em] text-slate-600', collapsed && 'lg:hidden')}>
              Administration
              <span className="rounded border border-brand-400/25 bg-brand-500/10 px-1 text-[9px] text-brand-300">ADMIN</span>
            </p>
            <div className="space-y-0.5">{renderItems(adminItems)}</div>
          </div>
        )}
      </nav>

      <div className="border-t border-white/[0.07] p-2.5">
        <div className={cn('flex items-center gap-2.5 rounded-xl px-1.5 py-1.5', collapsed && 'lg:justify-center lg:px-0')}>
          <Link
            to="/profile"
            onClick={onCloseMobile}
            className="flex min-w-0 flex-1 items-center gap-2.5 rounded-lg p-1 transition hover:bg-white/[0.05] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-400"
            title="Your profile"
          >
            <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-brand-500 to-accent-500 text-[11px] font-semibold text-navy-950">
              {initials(user?.displayName || user?.email)}
            </span>
            <span className={cn('min-w-0', collapsed && 'lg:hidden')}>
              <span className="block truncate text-xs font-medium text-slate-200">{user?.displayName || 'Operator'}</span>
              <span className="block truncate text-[10px] text-slate-500">{org}</span>
            </span>
          </Link>
          <button
            type="button"
            onClick={() => void logout()}
            className={cn(
              'rounded-lg p-1.5 text-slate-500 transition hover:bg-white/[0.06] hover:text-rose-300 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-400',
              collapsed && 'lg:hidden',
            )}
            aria-label="Sign out"
            title="Sign out"
          >
            <LogOut className="h-4 w-4" />
          </button>
        </div>
        <p className={cn('mt-1.5 px-2 text-[9px] uppercase tracking-[0.14em] text-slate-700', collapsed && 'lg:hidden')}>
          v1 · OSM tiles
        </p>
      </div>
    </aside>
  );
}
