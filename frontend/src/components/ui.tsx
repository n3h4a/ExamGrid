import clsx from 'clsx';
import { AlertTriangle, Loader2, X } from 'lucide-react';
import {
  useEffect,
  useId,
  useRef,
  type ButtonHTMLAttributes,
  type InputHTMLAttributes,
  type ReactNode,
  type SelectHTMLAttributes,
} from 'react';
import { ApiError } from '../api/client';

/* ------------------------------------------------------------------ buttons */

type Variant = 'primary' | 'secondary' | 'danger' | 'ghost';

const variants: Record<Variant, string> = {
  primary: 'bg-brand-700 text-white hover:bg-brand-600 disabled:bg-brand-700/50',
  secondary: 'bg-white text-slate-800 ring-1 ring-slate-300 hover:bg-slate-50 disabled:text-slate-400',
  danger: 'bg-red-600 text-white hover:bg-red-500 disabled:bg-red-600/50',
  ghost: 'text-slate-600 hover:bg-slate-100 disabled:text-slate-300',
};

export function Button({
  variant = 'primary',
  loading,
  className,
  children,
  disabled,
  ...rest
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant; loading?: boolean }) {
  return (
    <button
      {...rest}
      disabled={disabled || loading}
      className={clsx(
        'inline-flex items-center justify-center gap-2 rounded-md px-3.5 py-2 text-sm font-medium shadow-sm transition-colors disabled:cursor-not-allowed',
        variants[variant],
        className,
      )}
    >
      {loading && <Loader2 className="size-4 animate-spin" aria-hidden />}
      {children}
    </button>
  );
}

/* ------------------------------------------------------------------ layout blocks */

export function PageHeader({ title, description, actions }: { title: string; description?: ReactNode; actions?: ReactNode }) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight text-slate-900">{title}</h1>
        {description && <p className="mt-1 max-w-3xl text-sm text-slate-600">{description}</p>}
      </div>
      {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
    </div>
  );
}

export function Card({ title, actions, children, className }: { title?: ReactNode; actions?: ReactNode; children: ReactNode; className?: string }) {
  return (
    <section className={clsx('rounded-xl bg-white shadow-sm ring-1 ring-slate-200', className)}>
      {(title || actions) && (
        <header className="flex items-center justify-between gap-4 border-b border-slate-100 px-5 py-3">
          {title && <h2 className="text-sm font-semibold text-slate-800">{title}</h2>}
          {actions}
        </header>
      )}
      <div className="p-5">{children}</div>
    </section>
  );
}

export function StatTile({ label, value, hint, icon }: { label: string; value: ReactNode; hint?: ReactNode; icon?: ReactNode }) {
  return (
    <div className="rounded-xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
      <div className="flex items-center justify-between text-xs font-medium uppercase tracking-wide text-slate-500">
        {label}
        {icon && <span className="text-slate-400">{icon}</span>}
      </div>
      <div className="mt-2 text-2xl font-semibold tabular-nums text-slate-900">{value}</div>
      {hint && <div className="mt-1 text-xs text-slate-500">{hint}</div>}
    </div>
  );
}

type Tone = 'slate' | 'green' | 'amber' | 'red' | 'blue' | 'violet';
const tones: Record<Tone, string> = {
  slate: 'bg-slate-100 text-slate-700 ring-slate-200',
  green: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
  amber: 'bg-amber-50 text-amber-800 ring-amber-200',
  red: 'bg-red-50 text-red-700 ring-red-200',
  blue: 'bg-sky-50 text-sky-700 ring-sky-200',
  violet: 'bg-violet-50 text-violet-700 ring-violet-200',
};

export function Badge({ tone = 'slate', children }: { tone?: Tone; children: ReactNode }) {
  return (
    <span className={clsx('inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset', tones[tone])}>
      {children}
    </span>
  );
}

export function Spinner({ label = 'Loading…' }: { label?: string }) {
  return (
    <div role="status" className="flex items-center gap-2 py-8 text-sm text-slate-500">
      <Loader2 className="size-4 animate-spin" aria-hidden />
      {label}
    </div>
  );
}

export function EmptyState({ title, children, action }: { title: string; children?: ReactNode; action?: ReactNode }) {
  return (
    <div className="rounded-lg border border-dashed border-slate-300 px-6 py-10 text-center">
      <p className="text-sm font-medium text-slate-800">{title}</p>
      {children && <div className="mt-1 text-sm text-slate-500">{children}</div>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  );
}

/** Renders an API problem (or any error) with its detail, field errors and request id. */
export function ErrorBanner({ error, title = 'Something went wrong' }: { error: unknown; title?: string }) {
  if (!error) return null;
  const apiError = error instanceof ApiError ? error : null;
  const message = error instanceof Error ? error.message : String(error);
  return (
    <div role="alert" className="mb-4 rounded-lg bg-red-50 p-4 text-sm text-red-800 ring-1 ring-red-200">
      <div className="flex gap-2">
        <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden />
        <div>
          <p className="font-medium">{title}</p>
          <p className="mt-1">{message}</p>
          {apiError && apiError.fieldErrors.length > 0 && (
            <ul className="mt-2 list-disc pl-5">
              {apiError.fieldErrors.map((f) => (
                <li key={f.field + f.message}>
                  <span className="font-mono">{f.field}</span>: {f.message}
                </li>
              ))}
            </ul>
          )}
          {apiError?.requestId && <p className="mt-2 text-xs text-red-600">Reference: {apiError.requestId}</p>}
        </div>
      </div>
    </div>
  );
}

/* ------------------------------------------------------------------ forms */

export function Field({ label, hint, children, htmlFor }: { label: string; hint?: ReactNode; children: ReactNode; htmlFor?: string }) {
  return (
    <div className="space-y-1">
      <label htmlFor={htmlFor} className="block text-sm font-medium text-slate-700">
        {label}
      </label>
      {children}
      {hint && <p className="text-xs text-slate-500">{hint}</p>}
    </div>
  );
}

const control =
  'block w-full rounded-md border-0 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm ring-1 ring-inset ring-slate-300 placeholder:text-slate-400 focus:ring-2 focus:ring-brand-500 disabled:bg-slate-50';

export function TextInput({ label, hint, className, ...rest }: InputHTMLAttributes<HTMLInputElement> & { label?: string; hint?: ReactNode }) {
  const id = useId();
  const input = <input id={rest.id ?? id} {...rest} className={clsx(control, className)} />;
  return label ? (
    <Field label={label} hint={hint} htmlFor={rest.id ?? id}>
      {input}
    </Field>
  ) : (
    input
  );
}

export function SelectInput({
  label,
  hint,
  className,
  children,
  ...rest
}: SelectHTMLAttributes<HTMLSelectElement> & { label?: string; hint?: ReactNode }) {
  const id = useId();
  const select = (
    <select id={rest.id ?? id} {...rest} className={clsx(control, 'pr-8', className)}>
      {children}
    </select>
  );
  return label ? (
    <Field label={label} hint={hint} htmlFor={rest.id ?? id}>
      {select}
    </Field>
  ) : (
    select
  );
}

export function Checkbox({ label, ...rest }: InputHTMLAttributes<HTMLInputElement> & { label: ReactNode }) {
  return (
    <label className="inline-flex cursor-pointer items-center gap-2 text-sm text-slate-700">
      <input type="checkbox" {...rest} className="size-4 rounded border-slate-300 text-brand-700 focus:ring-brand-500" />
      {label}
    </label>
  );
}

/* ------------------------------------------------------------------ tables */

export function Table({ children }: { children: ReactNode }) {
  return (
    <div className="-mx-5 overflow-x-auto">
      <table className="min-w-full divide-y divide-slate-200 text-sm">{children}</table>
    </div>
  );
}

export function Th({ children, className }: { children?: ReactNode; className?: string }) {
  return (
    <th scope="col" className={clsx('px-5 py-2.5 text-left text-xs font-semibold uppercase tracking-wide text-slate-500', className)}>
      {children}
    </th>
  );
}

export function Td({ children, className }: { children?: ReactNode; className?: string }) {
  return <td className={clsx('whitespace-nowrap px-5 py-2.5 text-slate-700', className)}>{children}</td>;
}

export function Pagination({ page, totalPages, onPage }: { page: number; totalPages: number; onPage: (p: number) => void }) {
  if (totalPages <= 1) return null;
  return (
    <nav className="mt-4 flex items-center justify-end gap-2 text-sm" aria-label="Pagination">
      <Button variant="secondary" disabled={page === 0} onClick={() => onPage(page - 1)}>
        Previous
      </Button>
      <span className="text-slate-600">
        Page {page + 1} of {totalPages}
      </span>
      <Button variant="secondary" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>
        Next
      </Button>
    </nav>
  );
}

/* ------------------------------------------------------------------ modal */

export function Modal({ open, title, onClose, children, footer, wide }: {
  open: boolean;
  title: string;
  onClose: () => void;
  children: ReactNode;
  footer?: ReactNode;
  wide?: boolean;
}) {
  const panel = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    document.addEventListener('keydown', onKey);
    panel.current?.focus();
    return () => document.removeEventListener('keydown', onKey);
  }, [open, onClose]);
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto bg-slate-900/40 p-4 pt-[10vh]" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div
        ref={panel}
        role="dialog"
        aria-modal="true"
        aria-label={title}
        tabIndex={-1}
        className={clsx('w-full rounded-xl bg-white shadow-xl outline-none', wide ? 'max-w-4xl' : 'max-w-lg')}
      >
        <header className="flex items-center justify-between border-b border-slate-100 px-5 py-3">
          <h2 className="text-base font-semibold">{title}</h2>
          <button type="button" onClick={onClose} className="rounded p-1 text-slate-400 hover:bg-slate-100" aria-label="Close">
            <X className="size-4" />
          </button>
        </header>
        <div className="max-h-[70vh] overflow-y-auto px-5 py-4">{children}</div>
        {footer && <footer className="flex justify-end gap-2 border-t border-slate-100 px-5 py-3">{footer}</footer>}
      </div>
    </div>
  );
}
