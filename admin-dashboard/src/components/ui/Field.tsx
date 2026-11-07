import type {
  InputHTMLAttributes,
  SelectHTMLAttributes,
  TextareaHTMLAttributes,
  ReactNode,
} from 'react';
import { useId } from 'react';
import { ChevronDown } from 'lucide-react';
import { cn } from '@/lib/utils';

export const controlClass = (invalid?: boolean): string =>
  cn(
    'w-full rounded-lg border bg-navy-950/50 px-3 text-sm text-slate-100 placeholder:text-slate-500',
    'transition focus:outline-none focus:ring-2 disabled:cursor-not-allowed disabled:opacity-60',
    invalid
      ? 'border-rose-400/60 focus:border-rose-400 focus:ring-rose-400/30'
      : 'border-white/10 hover:border-white/20 focus:border-brand-400/60 focus:ring-brand-400/25',
  );

interface FieldProps {
  label?: string;
  htmlFor?: string;
  hint?: string;
  error?: string;
  required?: boolean;
  className?: string;
  children: ReactNode;
}

export function Field({ label, htmlFor, hint, error, required, className, children }: FieldProps) {
  return (
    <div className={cn('flex flex-col gap-1.5', className)}>
      {label && (
        <label htmlFor={htmlFor} className="text-xs font-medium tracking-wide text-slate-300">
          {label}
          {required && <span className="ml-1 text-rose-300/80">*</span>}
        </label>
      )}
      {children}
      {error ? (
        <p className="text-xs text-rose-300" role="alert">
          {error}
        </p>
      ) : hint ? (
        <p className="text-xs text-slate-500">{hint}</p>
      ) : null}
    </div>
  );
}

export interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  invalid?: boolean;
}

export function Input({ className, invalid, ...props }: InputProps) {
  return <input className={cn(controlClass(invalid), 'h-9', className)} {...props} />;
}

export interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  invalid?: boolean;
}

export function Select({ className, invalid, children, ...props }: SelectProps) {
  return (
    <div className="relative">
      <select
        className={cn(controlClass(invalid), 'h-9 cursor-pointer appearance-none pr-9', className)}
        {...props}
      >
        {children}
      </select>
      <ChevronDown
        className="pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-500"
        aria-hidden="true"
      />
    </div>
  );
}

export interface TextareaProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  invalid?: boolean;
}

export function Textarea({ className, invalid, ...props }: TextareaProps) {
  return <textarea className={cn(controlClass(invalid), 'min-h-[84px] py-2 font-mono text-xs leading-relaxed', className)} {...props} />;
}

interface SearchFieldProps {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  label: string;
  className?: string;
  children?: ReactNode;
}

export function SearchField({ value, onChange, placeholder, label, className, children }: SearchFieldProps) {
  const id = useId();
  return (
    <div className={cn('relative', className)}>
      <label htmlFor={id} className="sr-only">
        {label}
      </label>
      <span className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-500">
        {children}
      </span>
      <Input
        id={id}
        type="search"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder}
        className="h-9 pl-9"
        autoComplete="off"
      />
    </div>
  );
}
