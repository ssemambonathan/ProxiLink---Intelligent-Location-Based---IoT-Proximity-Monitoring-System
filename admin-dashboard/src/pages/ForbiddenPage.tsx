import { Link } from 'react-router-dom';
import { ArrowLeft, Map, ShieldAlert } from 'lucide-react';
import { BrandMark } from '@/components/BrandMark';
import { Button } from '@/components/ui/Button';
import { useAuth } from '@/context/AuthContext';

export function ForbiddenPage() {
  const { isAdmin, user } = useAuth();
  return (
    <div className="flex min-h-[65vh] items-center justify-center">
      <div className="w-full max-w-lg rounded-2xl border border-amber-400/15 bg-gradient-to-b from-amber-400/[0.05] to-transparent p-6 text-center shadow-card sm:p-8">
        <span className="mx-auto flex h-12 w-12 items-center justify-center rounded-2xl border border-amber-400/25 bg-amber-400/10 text-amber-300">
          <ShieldAlert className="h-5 w-5" aria-hidden="true" />
        </span>
        <p className="mt-5 font-mono text-[11px] uppercase tracking-[0.2em] text-amber-300/80">Error 403 · Forbidden</p>
        <h1 className="mt-2 text-xl font-semibold tracking-tight text-slate-50">Administration is restricted</h1>
        <p className="mx-auto mt-2 max-w-sm text-sm leading-relaxed text-slate-400">
          Your account{user?.email ? ` (${user.email})` : ''} does not hold the ADMIN role. The backend is authoritative;
          these routes are also blocked server-side.
        </p>
        <div className="mt-5 flex flex-wrap items-center justify-center gap-2">
          <Link to="/">
            <Button variant="secondary" leftIcon={<ArrowLeft className="h-4 w-4" />}>
              Back to overview
            </Button>
          </Link>
          <Link to="/map">
            <Button variant="outline" leftIcon={<Map className="h-4 w-4" />}>
              Open live map
            </Button>
          </Link>
        </div>
        <p className="mt-5 text-[11px] text-slate-600">
          Signed in with {isAdmin ? 'ADMIN' : 'non-admin'} roles. Ask an administrator to adjust access.
        </p>
      </div>
    </div>
  );
}

export function NotFoundPage() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-navy-900 bg-aurora px-4 py-10">
      <div className="w-full max-w-md rounded-2xl border border-white/[0.08] bg-navy-850/80 p-7 text-center shadow-glow backdrop-blur-xl">
        <BrandMark className="mx-auto h-12 w-12" />
        <p className="mt-6 font-mono text-[11px] uppercase tracking-[0.22em] text-slate-500">Error 404</p>
        <h1 className="mt-2 text-2xl font-semibold tracking-tight text-slate-50">Out of range</h1>
        <p className="mt-2 text-sm leading-relaxed text-slate-400">
          This route is not part of the ProxiLink console. It may have moved, or the link is stale.
        </p>
        <div className="mt-6 flex flex-wrap items-center justify-center gap-2">
          <Link to="/">
            <Button leftIcon={<ArrowLeft className="h-4 w-4" />}>Back to console</Button>
          </Link>
          <Link to="/map">
            <Button variant="secondary" leftIcon={<Map className="h-4 w-4" />}>
              Open map
            </Button>
          </Link>
        </div>
      </div>
    </div>
  );
}
