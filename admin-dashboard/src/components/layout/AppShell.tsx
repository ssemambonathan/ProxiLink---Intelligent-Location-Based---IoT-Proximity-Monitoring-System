import { Suspense, useEffect, useState } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { readLocalPreference, saveLocalPreference } from '@/lib/utils';
import { ConnectivityBanner } from '@/components/ui/States';
import { PageSkeleton } from '@/components/ui/Skeleton';
import { Sidebar } from '@/components/layout/Sidebar';
import { Topbar } from '@/components/layout/Topbar';
import { NotificationDrawer } from '@/components/layout/NotificationDrawer';

export function AppShell() {
  const { isAdmin } = useAuth();
  const location = useLocation();
  const [collapsed, setCollapsed] = useState(() => readLocalPreference('sidebar-collapsed') === '1');
  const [mobileOpen, setMobileOpen] = useState(false);

  useEffect(() => {
    saveLocalPreference('sidebar-collapsed', collapsed ? '1' : '0');
  }, [collapsed]);

  useEffect(() => {
    setMobileOpen(false);
  }, [location.pathname]);

  useEffect(() => {
    if (!mobileOpen) return;
    const previous = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = previous;
    };
  }, [mobileOpen]);

  return (
    <div className="relative flex h-screen overflow-hidden bg-navy-900">
      <a
        href="#main-content"
        className="sr-only z-[200] focus:not-sr-only focus:absolute focus:left-3 focus:top-3 focus:rounded-lg focus:bg-brand-500 focus:px-3 focus:py-2 focus:text-sm focus:font-medium focus:text-white"
      >
        Skip to content
      </a>

      {mobileOpen && (
        <div
          className="fixed inset-0 z-40 animate-fade-in bg-navy-950/70 backdrop-blur-sm lg:hidden"
          onClick={() => setMobileOpen(false)}
          aria-hidden="true"
        />
      )}

      <Sidebar
        isAdmin={isAdmin}
        collapsed={collapsed}
        onToggleCollapsed={() => setCollapsed((value) => !value)}
        mobileOpen={mobileOpen}
        onCloseMobile={() => setMobileOpen(false)}
      />

      <div className="flex min-w-0 flex-1 flex-col">
        <ConnectivityBanner />
        <Topbar onOpenMobileNav={() => setMobileOpen(true)} />
        <main id="main-content" className="relative min-h-0 flex-1 overflow-y-auto">
          <div className="pointer-events-none absolute inset-0 bg-grid-faint bg-grid opacity-40" aria-hidden="true" />
          <div className="pointer-events-none absolute inset-x-0 top-0 h-64 bg-gradient-to-b from-brand-500/[0.06] to-transparent" aria-hidden="true" />
          <div className="relative mx-auto w-full max-w-[1560px] px-3.5 py-4 sm:px-5 sm:py-5 lg:px-6 lg:py-6">
            <Suspense fallback={<PageSkeleton />}>
              <Outlet />
            </Suspense>
          </div>
        </main>
      </div>

      <NotificationDrawer />
    </div>
  );
}
