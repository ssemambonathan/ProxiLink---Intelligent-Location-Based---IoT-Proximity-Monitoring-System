import { useState, type FormEvent } from 'react';
import { Navigate, useLocation, useNavigate, Link } from 'react-router-dom';
import {
  Activity,
  ArrowRight,
  Eye,
  EyeOff,
  KeyRound,
  Lock,
  Mail,
  MapPin,
  Radar,
  ShieldCheck,
  TriangleAlert,
} from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { API_BASE_URL, ApiError, describeApiError, isApiError } from '@/lib/api';
import { BrandMark } from '@/components/BrandMark';
import { Button } from '@/components/ui/Button';
import { Field, Input } from '@/components/ui/Field';

const DEMO_PASSWORD = 'ProxiLink!Demo2026';
const demoAccounts = [
  { role: 'Administrator', email: 'admin@proxilink.dev', note: 'Full access incl. Administration' },
  { role: 'Standard user', email: 'demo@proxilink.dev', note: 'Org-scoped, no admin pages' },
];

export function LoginPage() {
  const { status, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<{ email?: string; password?: string }>({});
  const [error, setError] = useState<ApiError | null>(null);
  const [busy, setBusy] = useState(false);

  const state = location.state as { from?: unknown } | null;
  const from = state?.from;
  const redirectTo = typeof from === 'string' && from.startsWith('/') && !from.startsWith('//') ? from : '/';

  if (status === 'authenticated') return <Navigate to={redirectTo} replace />;

  const fillDemo = (accountEmail: string) => {
    setEmail(accountEmail);
    setPassword(DEMO_PASSWORD);
    setFieldErrors({});
    setError(null);
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const nextErrors: { email?: string; password?: string } = {};
    if (!email.trim()) nextErrors.email = 'Email is required.';
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) nextErrors.email = 'Enter a valid email address.';
    if (!password) nextErrors.password = 'Password is required.';
    setFieldErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) return;

    setBusy(true);
    setError(null);
    try {
      await login(email, password);
      navigate(redirectTo, { replace: true });
    } catch (caught) {
      setError(isApiError(caught) ? caught : new ApiError(0, 'UNKNOWN', 'Sign-in failed unexpectedly.'));
    } finally {
      setBusy(false);
    }
  };

  const errorCopy = error ? describeApiError(error) : null;

  return (
    <div className="relative min-h-screen overflow-hidden bg-navy-900">
      <div className="pointer-events-none absolute inset-0 bg-aurora" aria-hidden="true" />
      <div className="pointer-events-none absolute inset-0 bg-grid-faint bg-grid opacity-30" aria-hidden="true" />
      <div className="relative z-10 mx-auto flex min-h-screen w-full max-w-6xl flex-col px-4 py-6 sm:px-6 lg:grid lg:grid-cols-[1.05fr_0.95fr] lg:items-center lg:gap-16 lg:py-10">
        {/* Brand / narrative panel */}
        <div className="mb-8 lg:mb-0">
          <Link
            to="/login"
            className="inline-flex items-center gap-2.5 rounded-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-400"
            aria-label="ProxiLink console"
          >
            <BrandMark className="h-10 w-10" />
            <span>
              <span className="block text-base font-semibold tracking-tight text-slate-50">ProxiLink</span>
              <span className="block text-[10px] uppercase tracking-[0.22em] text-slate-500">Operations console</span>
            </span>
          </Link>

          <div className="mt-8 max-w-xl lg:mt-14">
            <p className="inline-flex items-center gap-2 rounded-full border border-brand-400/25 bg-brand-500/[0.08] px-3 py-1 text-[11px] font-medium text-brand-200">
              <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-accent-500" aria-hidden="true" />
              BLE proximity · GPS telemetry
            </p>
            <h1 className="mt-4 text-3xl font-semibold leading-[1.15] tracking-tight text-slate-50 sm:text-4xl">
              Know what is <span className="bg-gradient-to-r from-brand-300 via-brand-400 to-accent-400 bg-clip-text text-transparent">near</span>,
              in real time.
            </h1>
            <p className="mt-3 max-w-lg text-sm leading-relaxed text-slate-400">
              Operate registered beacons, watch live proximity bands and GPS fixes, and audit every
              security-relevant action across your organization — without pretending RSSI is a distance sensor.
            </p>

            <ul className="mt-7 hidden space-y-3 sm:block">
              {[
                { icon: Radar, title: 'Banded proximity', copy: 'IMMEDIATE → OUT_OF_RANGE, classified server-side from smoothed RSSI.' },
                { icon: Activity, title: 'Realtime channel', copy: 'WebSocket frames for proximity, location and device status with reconnect and backoff.' },
                { icon: ShieldCheck, title: 'Organization-scoped RBAC', copy: 'USER sees its organization; ADMIN unlocks users, audit and platform settings.' },
                { icon: MapPin, title: 'OpenStreetMap only', copy: 'No API keys, no vendor telemetry, dark basemap tuned for operations.' },
              ].map((feature) => (
                <li key={feature.title} className="flex items-start gap-3">
                  <span className="mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-lg border border-white/[0.08] bg-white/[0.04] text-brand-300">
                    <feature.icon className="h-3.5 w-3.5" aria-hidden="true" />
                  </span>
                  <div>
                    <p className="text-[13px] font-medium text-slate-200">{feature.title}</p>
                    <p className="mt-0.5 max-w-md text-xs leading-relaxed text-slate-500">{feature.copy}</p>
                  </div>
                </li>
              ))}
            </ul>
          </div>

          <p className="mt-10 hidden text-[11px] text-slate-600 lg:block">
            Spring Boot API · React 19 · Leaflet 1.9 · v1 contract
          </p>
        </div>

        {/* Auth panel */}
        <div className="w-full pb-8 lg:pb-0">
          <div className="rounded-2xl border border-white/[0.09] bg-navy-850/80 p-5 shadow-glow backdrop-blur-xl sm:p-7">
            <div className="flex items-start gap-3">
              <span className="flex h-10 w-10 items-center justify-center rounded-xl border border-brand-400/25 bg-brand-500/10 text-brand-300">
                <Lock className="h-[18px] w-[18px]" aria-hidden="true" />
              </span>
              <div>
                <h2 className="text-lg font-semibold tracking-tight text-slate-50">Operator sign-in</h2>
                <p className="mt-0.5 text-xs leading-relaxed text-slate-500">
                  Authenticate with your ProxiLink account to open the console.
                </p>
              </div>
            </div>

            {error && errorCopy && (
              <div className="mt-5 rounded-xl border border-rose-400/25 bg-rose-500/[0.06] p-3.5" role="alert">
                <div className="flex items-start gap-2.5">
                  <TriangleAlert className="mt-0.5 h-4 w-4 shrink-0 text-rose-300" aria-hidden="true" />
                  <div className="min-w-0">
                    <p className="text-sm font-medium text-rose-100">{errorCopy.title}</p>
                    <p className="mt-0.5 text-xs leading-relaxed text-rose-200/80">{errorCopy.description}</p>
                    {error.violations && Object.keys(error.violations).length > 0 && (
                      <ul className="mt-1.5 space-y-0.5 text-xs text-rose-200/90">
                        {Object.entries(error.violations).map(([field, message]) => (
                          <li key={field}>{field}: {message}</li>
                        ))}
                      </ul>
                    )}
                    {error.network && (
                      <div className="mt-2 rounded-lg border border-white/[0.08] bg-navy-950/50 p-2.5 text-[11px] leading-relaxed text-slate-400">
                        <p>
                          Expected API base <span className="font-mono text-slate-300">{API_BASE_URL}</span> (dev proxy →{' '}
                          <span className="font-mono text-slate-300">http://localhost:8080</span>).
                        </p>
                        <p className="mt-1">Start the backend, then submit again — nothing is mocked in this build.</p>
                      </div>
                    )}
                    <p className="mt-1.5 font-mono text-[10px] uppercase tracking-wider text-rose-300/50">
                      {error.code}{error.status > 0 ? ` · HTTP ${error.status}` : ''}
                    </p>
                  </div>
                </div>
              </div>
            )}

            <form className="mt-5 space-y-4" onSubmit={handleSubmit} noValidate>
              <Field label="Email" htmlFor="login-email" error={fieldErrors.email} required>
                <div className="relative">
                  <Mail className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-500" aria-hidden="true" />
                  <Input
                    id="login-email"
                    name="email"
                    type="email"
                    autoComplete="email"
                    autoFocus
                    placeholder="you@organization.com"
                    value={email}
                    onChange={(event) => setEmail(event.target.value)}
                    invalid={Boolean(fieldErrors.email)}
                    className="h-10 pl-9"
                    aria-invalid={Boolean(fieldErrors.email)}
                  />
                </div>
              </Field>

              <Field label="Password" htmlFor="login-password" error={fieldErrors.password} required>
                <div className="relative">
                  <KeyRound className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-500" aria-hidden="true" />
                  <Input
                    id="login-password"
                    name="password"
                    type={showPassword ? 'text' : 'password'}
                    autoComplete="current-password"
                    placeholder="••••••••••"
                    value={password}
                    onChange={(event) => setPassword(event.target.value)}
                    invalid={Boolean(fieldErrors.password)}
                    className="h-10 pl-9 pr-10"
                    aria-invalid={Boolean(fieldErrors.password)}
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword((value) => !value)}
                    className="absolute right-1.5 top-1/2 -translate-y-1/2 rounded-md p-1.5 text-slate-500 transition hover:text-slate-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-400"
                    aria-label={showPassword ? 'Hide password' : 'Show password'}
                    aria-pressed={showPassword}
                  >
                    {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                  </button>
                </div>
              </Field>

              <Button type="submit" size="lg" className="w-full" loading={busy} rightIcon={<ArrowRight className="h-4 w-4" />}>
                {busy ? 'Signing in…' : 'Sign in'}
              </Button>
            </form>

            <div className="mt-5 rounded-xl border border-white/[0.07] bg-white/[0.02] p-3">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <p className="text-[10px] font-semibold uppercase tracking-[0.16em] text-slate-500">Demo access</p>
                <span className="font-mono text-[10px] text-slate-500">{DEMO_PASSWORD}</span>
              </div>
              <div className="mt-2 grid gap-1.5 sm:grid-cols-2">
                {demoAccounts.map((account) => (
                  <button
                    key={account.email}
                    type="button"
                    onClick={() => fillDemo(account.email)}
                    className="group rounded-lg border border-white/[0.07] bg-navy-950/40 p-2.5 text-left transition hover:border-brand-400/30 hover:bg-brand-500/[0.06] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-400"
                    aria-label={`Fill ${account.role.toLowerCase()} demo credentials`}
                  >
                    <span className="flex items-center justify-between gap-2">
                      <span className="text-[11px] font-medium text-slate-200">{account.role}</span>
                      <span className="text-[10px] text-brand-300 opacity-0 transition group-hover:opacity-100">Use →</span>
                    </span>
                    <span className="mt-0.5 block truncate font-mono text-[10px] text-slate-500">{account.email}</span>
                    <span className="mt-1 block text-[10px] leading-tight text-slate-600">{account.note}</span>
                  </button>
                ))}
              </div>
              <p className="mt-2 text-[10px] leading-relaxed text-slate-600">
                Demo credentials only. Session tokens are kept in <span className="font-mono">localStorage</span> for this
                build — see the README for the XSS tradeoff.
              </p>
            </div>
          </div>
          <p className="mt-4 text-center text-[11px] text-slate-600 lg:hidden">
            OpenStreetMap tiles · no API keys · RSSI bands are estimates
          </p>
        </div>
      </div>
    </div>
  );
}
