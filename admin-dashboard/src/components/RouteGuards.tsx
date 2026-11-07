import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { BrandMark } from '@/components/BrandMark';
import { ForbiddenPage } from '@/pages/ForbiddenPage';

export function FullScreenLoader({ label = 'Restoring session…' }: { label?: string }) {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-4 bg-navy-900 bg-aurora" role="status" aria-live="polite">
      <div className="relative">
        <BrandMark className="h-12 w-12" />
        <span className="absolute -inset-3 animate-pulse rounded-2xl border border-brand-400/20" aria-hidden="true" />
      </div>
      <div className="text-center">
        <p className="text-sm font-medium text-slate-200">{label}</p>
        <p className="mt-0.5 text-xs text-slate-500">ProxiLink Console</p>
      </div>
    </div>
  );
}

export function RequireAuth() {
  const { status } = useAuth();
  const location = useLocation();

  if (status === 'booting') return <FullScreenLoader />;
  if (status === 'unauthenticated') {
    const from = `${location.pathname}${location.search}`;
    return <Navigate to="/login" replace state={{ from: from === '/' ? undefined : from }} />;
  }
  return <Outlet />;
}

export function RequireAdmin() {
  const { isAdmin } = useAuth();
  if (!isAdmin) return <ForbiddenPage />;
  return <Outlet />;
}
