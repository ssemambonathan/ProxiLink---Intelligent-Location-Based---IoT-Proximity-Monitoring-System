import { useEffect, useState, type FormEvent } from 'react';
import { Save } from 'lucide-react';
import { apiRequest, isApiError } from '@/lib/api';
import type { Device } from '@/lib/types';
import { deviceCategories } from '@/lib/meta';
import { categoryLabel } from '@/lib/meta';
import { Modal } from '@/components/ui/Modal';
import { Button } from '@/components/ui/Button';
import { Field, Input, Select, Textarea } from '@/components/ui/Field';
import { InlineError } from '@/components/ui/States';
import { useToast } from '@/context/ToastContext';

interface DeviceFormModalProps {
  open: boolean;
  device: Device | null;
  onClose: () => void;
  onSaved: (device: Device) => void;
}

interface FormState {
  name: string;
  deviceType: string;
  category: string;
  hardwareId: string;
  bleMac: string;
  serviceUuid: string;
  metadata: string;
}

const emptyForm: FormState = {
  name: '',
  deviceType: 'beacon',
  category: 'ASSET',
  hardwareId: '',
  bleMac: '',
  serviceUuid: '',
  metadata: '',
};

const macPattern = /^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$/;

export function DeviceFormModal({ open, device, onClose, onSaved }: DeviceFormModalProps) {
  const toast = useToast();
  const [form, setForm] = useState<FormState>(emptyForm);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [generalError, setGeneralError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!open) return;
    if (device) {
      setForm({
        name: device.name ?? '',
        deviceType: device.deviceType ?? '',
        category: device.category ?? 'ASSET',
        hardwareId: device.hardwareId ?? '',
        bleMac: device.bleMac ?? '',
        serviceUuid: device.serviceUuid ?? '',
        metadata: device.metadata && Object.keys(device.metadata).length > 0 ? JSON.stringify(device.metadata, null, 2) : '',
      });
    } else {
      setForm(emptyForm);
    }
    setErrors({});
    setGeneralError(null);
  }, [open, device]);

  const update = (key: keyof FormState, value: string) => {
    setForm((current) => ({ ...current, [key]: value }));
    setErrors((current) => {
      if (!current[key]) return current;
      const next = { ...current };
      delete next[key];
      return next;
    });
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const nextErrors: Record<string, string> = {};
    if (form.name.trim().length < 2) nextErrors.name = 'Name must be at least 2 characters.';
    if (!deviceCategories.includes(form.category as (typeof deviceCategories)[number])) nextErrors.category = 'Choose a category.';
    if (!form.hardwareId.trim() && !form.bleMac.trim() && !form.serviceUuid.trim()) {
      nextErrors.hardwareId = 'Provide at least one identifier (hardware ID, BLE MAC or service UUID).';
    }
    if (form.bleMac.trim() && !macPattern.test(form.bleMac.trim())) {
      nextErrors.bleMac = 'Use six colon-separated hex pairs, e.g. C4:DD:57:11:22:33.';
    }

    let metadata: Record<string, unknown> | undefined;
    if (form.metadata.trim()) {
      try {
        const parsed: unknown = JSON.parse(form.metadata);
        if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
          nextErrors.metadata = 'Metadata must be a JSON object.';
        } else {
          metadata = parsed as Record<string, unknown>;
        }
      } catch {
        nextErrors.metadata = 'Metadata is not valid JSON.';
      }
    }

    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) return;

    const payload = {
      name: form.name.trim(),
      deviceType: form.deviceType.trim() || undefined,
      category: form.category,
      hardwareId: form.hardwareId.trim() || undefined,
      bleMac: form.bleMac.trim() || undefined,
      serviceUuid: form.serviceUuid.trim() || undefined,
      metadata,
    };

    setBusy(true);
    setGeneralError(null);
    try {
      const saved = device
        ? await apiRequest<Device>(`/devices/${device.id}`, { method: 'PATCH', body: payload })
        : await apiRequest<Device>('/devices', { method: 'POST', body: payload });
      toast.success(device ? 'Device updated' : 'Device registered', saved.name);
      onSaved(saved);
      onClose();
    } catch (caught) {
      if (isApiError(caught)) {
        if (Object.keys(caught.violations).length > 0) {
          setErrors(caught.violations);
          setGeneralError('Please fix the highlighted fields.');
        } else {
          setGeneralError(caught.message);
        }
      } else {
        setGeneralError('Could not save the device. Check your connection and retry.');
      }
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal
      open={open}
      onClose={busy ? () => undefined : onClose}
      title={device ? `Edit ${device.name}` : 'Register device'}
      description={
        device
          ? 'PATCH /devices/{id} — partial update of the identity fields. Activation is managed separately.'
          : 'POST /devices — register a beacon or sensor so phones can correlate BLE scans and GPS.'
      }
      size="lg"
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={busy}>
            Cancel
          </Button>
          <Button type="submit" form="device-form" loading={busy} leftIcon={<Save className="h-4 w-4" />}>
            {device ? 'Save changes' : 'Register device'}
          </Button>
        </>
      }
    >
      <form id="device-form" className="space-y-4" onSubmit={handleSubmit} noValidate>
        {generalError && <InlineError>{generalError}</InlineError>}

        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Device name" htmlFor="device-name" required error={errors.name} className="sm:col-span-2">
            <Input
              id="device-name"
              value={form.name}
              onChange={(event) => update('name', event.target.value)}
              placeholder="Dock door beacon"
              invalid={Boolean(errors.name)}
              maxLength={120}
              autoFocus
            />
          </Field>

          <Field label="Category" htmlFor="device-category" required error={errors.category}>
            <Select id="device-category" value={form.category} onChange={(event) => update('category', event.target.value)} invalid={Boolean(errors.category)}>
              {deviceCategories.map((option) => (
                <option key={option} value={option}>
                  {categoryLabel(option)}
                </option>
              ))}
            </Select>
          </Field>

          <Field label="Device type" htmlFor="device-type" hint="Free-form, e.g. beacon, tag, gateway" error={errors.deviceType}>
            <Input
              id="device-type"
              list="device-type-suggestions"
              value={form.deviceType}
              onChange={(event) => update('deviceType', event.target.value)}
              placeholder="beacon"
              maxLength={64}
              invalid={Boolean(errors.deviceType)}
            />
            <datalist id="device-type-suggestions">
              {['beacon', 'sensor', 'tag', 'wearable', 'gateway'].map((option) => (
                <option key={option} value={option} />
              ))}
            </datalist>
          </Field>

          <Field
            label="Hardware ID"
            htmlFor="device-hardware"
            error={errors.hardwareId}
            hint="Identifier burned into the device firmware."
          >
            <Input
              id="device-hardware"
              value={form.hardwareId}
              onChange={(event) => update('hardwareId', event.target.value)}
              placeholder="esp32-a1b2c3"
              className="font-mono"
              invalid={Boolean(errors.hardwareId)}
              maxLength={128}
            />
          </Field>

          <Field label="BLE MAC" htmlFor="device-mac" error={errors.bleMac} hint="Optional; Android may randomise MAC addresses.">
            <Input
              id="device-mac"
              value={form.bleMac}
              onChange={(event) => update('bleMac', event.target.value)}
              placeholder="C4:DD:57:11:22:33"
              className="font-mono uppercase"
              invalid={Boolean(errors.bleMac)}
              maxLength={17}
            />
          </Field>

          <Field
            label="Service UUID"
            htmlFor="device-uuid"
            error={errors.serviceUuid}
            hint="Advertised GATT service used for identification."
            className="sm:col-span-2"
          >
            <Input
              id="device-uuid"
              value={form.serviceUuid}
              onChange={(event) => update('serviceUuid', event.target.value)}
              placeholder="6e400001-b5a3-f393-e0a9-e50e24dcca9e"
              className="font-mono"
              invalid={Boolean(errors.serviceUuid)}
              maxLength={64}
            />
          </Field>

          <Field
            label="Metadata (JSON)"
            htmlFor="device-metadata"
            error={errors.metadata}
            hint='Optional object, e.g. {"floor":"1","zone":"dock"}'
            className="sm:col-span-2"
          >
            <Textarea
              id="device-metadata"
              value={form.metadata}
              onChange={(event) => update('metadata', event.target.value)}
              placeholder={'{\n  "floor": "1",\n  "zone": "dock"\n}'}
              invalid={Boolean(errors.metadata)}
              spellCheck={false}
            />
          </Field>
        </div>

        <p className="rounded-lg border border-white/[0.06] bg-white/[0.02] px-3 py-2 text-[11px] leading-relaxed text-slate-500">
          Status is derived by the platform from live events; it cannot be set manually. Deactivating or deleting a device
          is reversible only by reactivating it.
        </p>
      </form>
    </Modal>
  );
}
