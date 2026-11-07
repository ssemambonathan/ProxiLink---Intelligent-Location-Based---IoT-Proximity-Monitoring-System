import { useEffect, useState, type FormEvent } from 'react';
import {
  AlertTriangle,
  Gauge,
  Info,
  RotateCcw,
  Save,
  SlidersHorizontal,
  Timer,
  Undo2,
} from 'lucide-react';
import { useApi } from '@/hooks/useApi';
import { apiRequest, errorMessage, isApiError } from '@/lib/api';
import type { PublicConfig, Settings } from '@/lib/types';
import { relativeTime } from '@/lib/format';
import { cardWrap } from '@/lib/styles';
import { useToast } from '@/context/ToastContext';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card, CardBody, CardHeader } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Field, Input } from '@/components/ui/Field';
import { Chip, ProximityChip } from '@/components/ui/Badge';
import { Skeleton } from '@/components/ui/Skeleton';
import { ErrorState, InlineError } from '@/components/ui/States';

/**
 * Administration → Settings.
 *
 * Reads GET /settings (thresholds, intervals, retention) and persists edits with
 * PATCH /settings. scanIntervalSeconds is not part of the documented settings
 * payload: the current value is read from the public config endpoint and only
 * sent back when an admin actually changes it (see README → API assumptions).
 */

interface FormState {
  immediate: string;
  veryClose: string;
  nearby: string;
  far: string;
  locationInterval: string;
  scanInterval: string;
  offlineTimeout: string;
  retention: string;
}

type FormKey = keyof FormState;

interface SettingsPayload {
  proximityThresholds: { immediate: number; veryClose: number; nearby: number; far: number };
  locationIntervalSeconds: number;
  scanIntervalSeconds?: number;
  offlineTimeoutSeconds: number;
  dataRetentionDays: number;
}

const DEFAULT_FORM: FormState = {
  immediate: '-50',
  veryClose: '-65',
  nearby: '-80',
  far: '-95',
  locationInterval: '30',
  scanInterval: '15',
  offlineTimeout: '300',
  retention: '90',
};

const EMPTY_FORM: FormState = {
  immediate: '',
  veryClose: '',
  nearby: '',
  far: '',
  locationInterval: '',
  scanInterval: '',
  offlineTimeout: '',
  retention: '',
};

const THRESHOLD_KEYS: FormKey[] = ['immediate', 'veryClose', 'nearby', 'far'];

function parseInteger(value: string): number | null {
  const trimmed = value.trim();
  if (!/^-?\d+$/.test(trimmed)) return null;
  const parsed = Number(trimmed);
  return Number.isSafeInteger(parsed) ? parsed : null;
}

function toForm(settings: Settings, scanFallback?: number): FormState {
  const thresholds = settings.proximityThresholds ?? { immediate: -50, veryClose: -65, nearby: -80, far: -95 };
  return {
    immediate: String(thresholds.immediate),
    veryClose: String(thresholds.veryClose),
    nearby: String(thresholds.nearby),
    far: String(thresholds.far),
    locationInterval: String(settings.locationIntervalSeconds),
    scanInterval: String(settings.scanIntervalSeconds ?? scanFallback ?? 15),
    offlineTimeout: String(settings.offlineTimeoutSeconds),
    retention: String(settings.dataRetentionDays),
  };
}

const snapshot = (form: FormState): string => JSON.stringify(form);

function numberError(value: string | undefined, min: number, max: number, unit: string): string | undefined {
  if (value === undefined) return undefined;
  const parsed = parseInteger(value);
  if (parsed === null) return 'Enter a whole number.';
  if (parsed < min || parsed > max) return `Must be between ${min} and ${max} ${unit}.`;
  return undefined;
}

/** Maps backend field violations (e.g. proximityThresholds.immediate) onto form fields. */
function normaliseViolations(violations: Record<string, string>): Partial<Record<FormKey, string>> {
  const aliases: Record<string, FormKey> = {
    immediate: 'immediate',
    veryclose: 'veryClose',
    nearby: 'nearby',
    far: 'far',
    locationintervalseconds: 'locationInterval',
    scanintervalseconds: 'scanInterval',
    offlinetimeoutseconds: 'offlineTimeout',
    dataretentiondays: 'retention',
  };
  const mapped: Partial<Record<FormKey, string>> = {};
  Object.entries(violations).forEach(([key, message]) => {
    const leaf = key.split('.').pop()?.toLowerCase() ?? key.toLowerCase();
    const field = aliases[leaf];
    if (field) mapped[field] = message;
  });
  return mapped;
}

export function SettingsPage() {
  const toast = useToast();
  const settingsQuery = useApi<Settings>('/settings');
  const publicConfig = useApi<PublicConfig>('/config/public');

  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [baseline, setBaseline] = useState<string>('');
  const [initialScan, setInitialScan] = useState<number | null>(null);
  const [hydrated, setHydrated] = useState(false);
  const [saving, setSaving] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);
  const [violations, setViolations] = useState<Partial<Record<FormKey, string>>>({});
  const [savedAt, setSavedAt] = useState<number | null>(null);

  useEffect(() => {
    if (hydrated || !settingsQuery.data || publicConfig.loading) return;
    const next = toForm(settingsQuery.data, publicConfig.data?.scanIntervalSeconds);
    setForm(next);
    setBaseline(snapshot(next));
    setInitialScan(parseInteger(next.scanInterval));
    setHydrated(true);
  }, [hydrated, settingsQuery.data, publicConfig.loading, publicConfig.data]);

  const update = (key: FormKey, value: string) => {
    setForm((current) => ({ ...current, [key]: value }));
    setServerError(null);
    setViolations((current) => {
      if (!current[key]) return current;
      const next = { ...current };
      delete next[key];
      return next;
    });
  };

  const errors: Partial<Record<FormKey, string>> = {};
  THRESHOLD_KEYS.forEach((key) => {
    const message = numberError(form[key], -127, -1, 'dBm');
    if (message) errors[key] = message;
  });
  errors.locationInterval = numberError(form.locationInterval, 1, 3600, 'seconds');
  errors.scanInterval = numberError(form.scanInterval, 1, 3600, 'seconds');
  errors.offlineTimeout = numberError(form.offlineTimeout, 30, 86400, 'seconds');
  errors.retention = numberError(form.retention, 1, 3650, 'days');

  const thresholds = {
    immediate: parseInteger(form.immediate),
    veryClose: parseInteger(form.veryClose),
    nearby: parseInteger(form.nearby),
    far: parseInteger(form.far),
  };
  const locationInterval = parseInteger(form.locationInterval);
  const scanInterval = parseInteger(form.scanInterval);
  const offlineTimeout = parseInteger(form.offlineTimeout);
  const retention = parseInteger(form.retention);

  const thresholdsUsable = THRESHOLD_KEYS.every((key) => thresholds[key as keyof typeof thresholds] !== null);
  const orderingValid =
    thresholdsUsable &&
    0 > (thresholds.immediate as number) &&
    (thresholds.immediate as number) > (thresholds.veryClose as number) &&
    (thresholds.veryClose as number) > (thresholds.nearby as number) &&
    (thresholds.nearby as number) > (thresholds.far as number) &&
    (thresholds.far as number) >= -127;
  const orderingError = thresholdsUsable && !orderingValid;

  const hasErrors =
    Object.values(errors).some(Boolean) ||
    orderingError ||
    [locationInterval, scanInterval, offlineTimeout, retention].some((value) => value === null);

  const offlineWarning =
    locationInterval !== null &&
    offlineTimeout !== null &&
    offlineTimeout <= locationInterval &&
    !errors.offlineTimeout;

  const payload: SettingsPayload | null = hasErrors
    ? null
    : {
        proximityThresholds: {
          immediate: thresholds.immediate as number,
          veryClose: thresholds.veryClose as number,
          nearby: thresholds.nearby as number,
          far: thresholds.far as number,
        },
        locationIntervalSeconds: locationInterval as number,
        ...(initialScan === null || scanInterval !== initialScan ? { scanIntervalSeconds: scanInterval as number } : {}),
        offlineTimeoutSeconds: offlineTimeout as number,
        dataRetentionDays: retention as number,
      };

  const dirty = hydrated && snapshot(form) !== baseline;
  const initialError = settingsQuery.error && !settingsQuery.data && !settingsQuery.loading;

  const handleSave = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!payload || saving || !dirty) return;
    setSaving(true);
    setServerError(null);
    setViolations({});
    try {
      const response = await apiRequest<Settings | undefined>('/settings', { method: 'PATCH', body: payload });
      setBaseline(snapshot(form));
      setSavedAt(Date.now());
      if (response && response.proximityThresholds) {
        setInitialScan(response.scanIntervalSeconds ?? scanInterval);
      } else {
        setInitialScan(scanInterval);
      }
      toast.success('Settings saved', 'New bands are used by the server for subsequent classifications.');
    } catch (caught) {
      const message = errorMessage(caught);
      if (isApiError(caught)) setViolations(normaliseViolations(caught.violations));
      setServerError(message);
      toast.error('Could not save settings', message);
    } finally {
      setSaving(false);
    }
  };

  const discard = () => {
    setForm(JSON.parse(baseline) as FormState);
    setServerError(null);
    setViolations({});
  };

  const resetDefaults = () => {
    setForm({ ...DEFAULT_FORM });
    setServerError(null);
    setViolations({});
  };

  const retryAll = () => {
    setHydrated(false);
    settingsQuery.reload();
    publicConfig.reload();
  };

  const formatBand = (from: number | null, upperExclusive: number | null): string => {
    if (from === null || upperExclusive === null || !orderingValid) return '—';
    return `${from} to ${upperExclusive - 1} dBm`;
  };

  const bandRows = [
    {
      level: 'IMMEDIATE',
      range: thresholds.immediate !== null && orderingValid ? `${thresholds.immediate} dBm or stronger` : '—',
    },
    { level: 'VERY_CLOSE', range: formatBand(thresholds.veryClose, thresholds.immediate) },
    { level: 'NEARBY', range: formatBand(thresholds.nearby, thresholds.veryClose) },
    { level: 'FAR', range: formatBand(thresholds.far, thresholds.nearby) },
    {
      level: 'OUT_OF_RANGE',
      range: thresholds.far !== null && orderingValid ? `weaker than ${thresholds.far} dBm` : '—',
    },
  ];

  const fieldError = (key: FormKey): string | undefined => violations[key] ?? errors[key];

  return (
    <div className={cardWrap}>
      <PageHeader
        eyebrow="Administration"
        title="Settings"
        description="Proximity bands, reporting intervals and retention. Changes are applied server-side and audited."
        actions={
          <span aria-live="polite" className="flex items-center gap-2">
            {hydrated && (
              <Chip tone={dirty ? 'warning' : 'success'} dot>
                {dirty ? 'Unsaved changes' : 'In sync'}
              </Chip>
            )}
            {savedAt && !dirty && (
              <span className="text-[11px] text-slate-500">Saved {relativeTime(new Date(savedAt).toISOString())}</span>
            )}
          </span>
        }
      />

      {initialError ? (
        <Card>
          <CardBody>
            <ErrorState error={settingsQuery.error!} onRetry={retryAll} />
          </CardBody>
        </Card>
      ) : !hydrated ? (
        <div className="space-y-4" role="status" aria-label="Loading settings">
          <Card>
            <CardBody className="space-y-4">
              <Skeleton className="h-4 w-44" />
              <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
                {Array.from({ length: 4 }).map((_, index) => (
                  <Skeleton key={index} className="h-9 w-full" />
                ))}
              </div>
            </CardBody>
          </Card>
          <Card>
            <CardBody className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              {Array.from({ length: 4 }).map((_, index) => (
                <Skeleton key={index} className="h-9 w-full" />
              ))}
            </CardBody>
          </Card>
        </div>
      ) : (
        <form className="space-y-4" onSubmit={(event) => void handleSave(event)} noValidate>
          {serverError && <InlineError>{serverError}</InlineError>}

          <div className="grid gap-4 xl:grid-cols-[minmax(0,1.35fr)_minmax(0,0.65fr)]">
            <Card>
              <CardHeader
                title="Proximity thresholds"
                description="RSSI cut-offs in dBm. Each band must be weaker than the one before it."
                icon={SlidersHorizontal}
              />
              <CardBody className="space-y-4">
                <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
                  <Field
                    label="Immediate"
                    htmlFor="threshold-immediate"
                    hint="Strongest band"
                    error={fieldError('immediate')}
                  >
                    <Input
                      id="threshold-immediate"
                      type="number"
                      inputMode="numeric"
                      min={-127}
                      max={-1}
                      step={1}
                      value={form.immediate}
                      invalid={Boolean(fieldError('immediate'))}
                      onChange={(event) => update('immediate', event.target.value)}
                    />
                  </Field>
                  <Field
                    label="Very close"
                    htmlFor="threshold-very-close"
                    hint="Below immediate"
                    error={fieldError('veryClose')}
                  >
                    <Input
                      id="threshold-very-close"
                      type="number"
                      inputMode="numeric"
                      min={-127}
                      max={-1}
                      step={1}
                      value={form.veryClose}
                      invalid={Boolean(fieldError('veryClose'))}
                      onChange={(event) => update('veryClose', event.target.value)}
                    />
                  </Field>
                  <Field
                    label="Nearby"
                    htmlFor="threshold-nearby"
                    hint="Below very close"
                    error={fieldError('nearby')}
                  >
                    <Input
                      id="threshold-nearby"
                      type="number"
                      inputMode="numeric"
                      min={-127}
                      max={-1}
                      step={1}
                      value={form.nearby}
                      invalid={Boolean(fieldError('nearby'))}
                      onChange={(event) => update('nearby', event.target.value)}
                    />
                  </Field>
                  <Field
                    label="Far"
                    htmlFor="threshold-far"
                    hint="Weakest band ≥ -127"
                    error={fieldError('far')}
                  >
                    <Input
                      id="threshold-far"
                      type="number"
                      inputMode="numeric"
                      min={-127}
                      max={-1}
                      step={1}
                      value={form.far}
                      invalid={Boolean(fieldError('far'))}
                      onChange={(event) => update('far', event.target.value)}
                    />
                  </Field>
                </div>

                {orderingError && (
                  <InlineError>
                    Bands must satisfy 0 &gt; immediate &gt; very close &gt; nearby &gt; far ≥ -127 dBm.
                  </InlineError>
                )}

                <div className="flex flex-wrap items-start gap-2 rounded-lg border border-white/[0.06] bg-white/[0.015] px-3 py-2.5 text-[11px] leading-relaxed text-slate-500">
                  <Info className="mt-0.5 h-3.5 w-3.5 shrink-0 text-slate-500" aria-hidden="true" />
                  <p>
                    RSSI is an estimate, not a distance. The backend classifies the median-of-five, EMA-smoothed
                    value, so bands are applied to server data — estimated metres on event rows are indicative only.
                  </p>
                </div>
              </CardBody>
            </Card>

            <Card>
              <CardHeader title="Band preview" description="How the values above classify a reading." icon={Gauge} />
              <CardBody className="space-y-2">
                {bandRows.map((row) => (
                  <div
                    key={row.level}
                    className="flex items-center justify-between gap-3 rounded-lg border border-white/[0.05] bg-white/[0.02] px-2.5 py-2"
                  >
                    <ProximityChip level={row.level} />
                    <span className="text-right font-mono text-[10px] text-slate-400">{row.range}</span>
                  </div>
                ))}
                <p className="pt-1 text-[10px] leading-relaxed text-slate-600">
                  Readings below the far band are treated as out of range and excluded from proximity alerts.
                </p>
              </CardBody>
            </Card>
          </div>

          <Card>
            <CardHeader
              title="Timing & retention"
              description="How often clients report, when devices are considered offline, and how long history is kept."
              icon={Timer}
            />
            <CardBody className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              <Field
                label="Location interval"
                htmlFor="location-interval"
                hint="Expected GPS upload cadence."
                error={fieldError('locationInterval')}
              >
                <div className="relative">
                  <Input
                    id="location-interval"
                    type="number"
                    inputMode="numeric"
                    min={1}
                    max={3600}
                    step={1}
                    value={form.locationInterval}
                    invalid={Boolean(fieldError('locationInterval'))}
                    onChange={(event) => update('locationInterval', event.target.value)}
                    className="pr-16"
                  />
                  <span className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-[10px] text-slate-500">
                    seconds
                  </span>
                </div>
              </Field>

              <Field
                label="Scan interval"
                htmlFor="scan-interval"
                hint={
                  publicConfig.data?.scanIntervalSeconds !== undefined && initialScan !== null && !dirty
                    ? `Public config: ${publicConfig.data.scanIntervalSeconds}s`
                    : 'Written only when changed.'
                }
                error={fieldError('scanInterval')}
              >
                <div className="relative">
                  <Input
                    id="scan-interval"
                    type="number"
                    inputMode="numeric"
                    min={1}
                    max={3600}
                    step={1}
                    value={form.scanInterval}
                    invalid={Boolean(fieldError('scanInterval'))}
                    onChange={(event) => update('scanInterval', event.target.value)}
                    className="pr-16"
                  />
                  <span className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-[10px] text-slate-500">
                    seconds
                  </span>
                </div>
              </Field>

              <Field
                label="Offline timeout"
                htmlFor="offline-timeout"
                hint="Silence before a device is marked offline."
                error={fieldError('offlineTimeout')}
              >
                <div className="relative">
                  <Input
                    id="offline-timeout"
                    type="number"
                    inputMode="numeric"
                    min={30}
                    max={86400}
                    step={1}
                    value={form.offlineTimeout}
                    invalid={Boolean(fieldError('offlineTimeout'))}
                    onChange={(event) => update('offlineTimeout', event.target.value)}
                    className="pr-16"
                  />
                  <span className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-[10px] text-slate-500">
                    seconds
                  </span>
                </div>
              </Field>

              <Field
                label="Data retention"
                htmlFor="retention"
                hint="History window before pruning."
                error={fieldError('retention')}
              >
                <div className="relative">
                  <Input
                    id="retention"
                    type="number"
                    inputMode="numeric"
                    min={1}
                    max={3650}
                    step={1}
                    value={form.retention}
                    invalid={Boolean(fieldError('retention'))}
                    onChange={(event) => update('retention', event.target.value)}
                    className="pr-12"
                  />
                  <span className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-[10px] text-slate-500">
                    days
                  </span>
                </div>
              </Field>

              {offlineWarning && (
                <p className="flex items-start gap-1.5 text-[11px] text-amber-300/90 sm:col-span-2 lg:col-span-4">
                  <AlertTriangle className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                  The offline timeout is not longer than the location interval — devices could flap between online and
                  offline between reports.
                </p>
              )}
            </CardBody>
          </Card>

          <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-white/[0.07] bg-navy-900/50 px-3.5 py-3 backdrop-blur-sm">
            <p className="text-[11px] text-slate-500">
              {dirty ? 'You have unsaved settings changes.' : 'All changes are saved.'} Saving writes a SETTINGS_UPDATE
              audit entry.
            </p>
            <div className="flex flex-wrap items-center gap-2">
              <Button
                variant="ghost"
                size="sm"
                onClick={resetDefaults}
                disabled={saving}
                leftIcon={<RotateCcw className="h-3.5 w-3.5" />}
              >
                Reset to defaults
              </Button>
              <Button
                variant="secondary"
                size="sm"
                onClick={discard}
                disabled={!dirty || saving}
                leftIcon={<Undo2 className="h-3.5 w-3.5" />}
              >
                Discard
              </Button>
              <Button
                type="submit"
                size="sm"
                loading={saving}
                disabled={!dirty || hasErrors}
                leftIcon={<Save className="h-3.5 w-3.5" />}
              >
                Save changes
              </Button>
            </div>
          </div>
        </form>
      )}
    </div>
  );
}
