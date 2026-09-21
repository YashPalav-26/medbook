import type { ReactNode } from 'react';
import { Loader2 } from 'lucide-react';

/**
 * Shared feedback primitives used on every data-driven page, so loading, empty,
 * error and status states look identical across patient and admin areas.
 */

/** Appointment/status badge — color plus a word, never color alone (a11y). */
export function StatusBadge({ status }: { status: string }) {
  const styles: Record<string, string> = {
    SCHEDULED: 'bg-info-100 text-info-700',
    CONFIRMED: 'bg-brand-100 text-brand-700',
    COMPLETED: 'bg-ink-100 text-ink-700',
    CANCELLED: 'bg-danger-100 text-danger-700',
    NO_SHOW: 'bg-warning-100 text-warning-700',
    Active: 'bg-success-100 text-success-700',
    Inactive: 'bg-ink-100 text-ink-600',
    Open: 'bg-success-100 text-success-700',
    Closed: 'bg-ink-100 text-ink-600',
  };
  const label = status.replace('_', ' ');
  return (
    <span
      className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${styles[status] ?? 'bg-ink-100 text-ink-700'}`}
    >
      {label}
    </span>
  );
}

/** Boolean active/inactive pill for admin lists. */
export function ActiveBadge({ active }: { active: boolean }) {
  return <StatusBadge status={active ? 'Active' : 'Inactive'} />;
}

/** Centered empty/loading placeholder with an optional action. */
export function EmptyState({
  icon,
  title,
  message,
  loading = false,
  action,
}: {
  icon?: ReactNode;
  title?: string;
  message: string;
  loading?: boolean;
  action?: ReactNode;
}) {
  if (loading) {
    return (
      <div className="flex flex-col items-center justify-center gap-2 py-12 text-ink-500">
        <Loader2 className="h-5 w-5 animate-spin text-brand-500" aria-hidden="true" />
        <span>Loading…</span>
      </div>
    );
  }
  return (
    <div className="flex flex-col items-center justify-center gap-2 py-12 text-center text-ink-500">
      {icon && <div className="text-ink-300">{icon}</div>}
      {title && <p className="font-medium text-ink-700">{title}</p>}
      <p className="max-w-sm">{message}</p>
      {action && <div className="mt-2">{action}</div>}
    </div>
  );
}

/** Inline error callout, with optional retry — never a raw backend dump. */
export function ErrorBox({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div role="alert" className="rounded-card border border-danger-600/30 bg-danger-100/60 p-4">
      <p className="text-danger-700">{message}</p>
      {onRetry && (
        <button
          type="button"
          onClick={onRetry}
          className="mt-2 text-sm font-medium text-danger-700 underline hover:no-underline"
        >
          Try again
        </button>
      )}
    </div>
  );
}

/** Skeleton line for loading placeholders. */
export function Skeleton({ className = '' }: { className?: string }) {
  return <div aria-hidden="true" className={`animate-pulse rounded bg-ink-100 ${className}`} />;
}

/** Small inline spinner for buttons and inline waits. */
export function Spinner({ className = '' }: { className?: string }) {
  return <Loader2 aria-hidden="true" className={`h-4 w-4 animate-spin ${className}`} />;
}
