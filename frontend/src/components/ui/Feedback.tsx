import type { ReactNode } from 'react';
import Alert from '@mui/material/Alert';
import Chip from '@mui/material/Chip';
import Skeleton from '@mui/material/Skeleton';
import CircularProgress from '@mui/material/CircularProgress';
import Button from '@mui/material/Button';
import { RefreshCw } from 'lucide-react';

/**
 * Shared feedback primitives used on every data-driven page, so loading, empty,
 * error and status states look identical across patient and admin areas.
 *
 * These are now adapters over MUI primitives — Alert, Chip, Skeleton,
 * CircularProgress — with MedBook token styling from the central MUI theme.
 */

/** Appointment/status colors: never color alone — the chip carries the words. */
const STATUS_CHIP: Record<string, { color: 'info' | 'success' | 'default' | 'error' | 'warning'; label: string }> = {
  SCHEDULED: { color: 'info', label: 'Scheduled' },
  CONFIRMED: { color: 'success', label: 'Confirmed' },
  COMPLETED: { color: 'default', label: 'Completed' },
  CANCELLED: { color: 'error', label: 'Cancelled' },
  NO_SHOW: { color: 'warning', label: 'No show' },
  Active: { color: 'success', label: 'Active' },
  Inactive: { color: 'default', label: 'Inactive' },
  Open: { color: 'success', label: 'Open' },
  Closed: { color: 'default', label: 'Closed' },
};

/** Appointment/status badge — color plus a word, never color alone (a11y). */
export function StatusBadge({ status }: { status: string }) {
  const chip = STATUS_CHIP[status];
  return (
    <Chip
      size="small"
      label={chip?.label ?? status.replace('_', ' ')}
      color={chip?.color ?? 'default'}
      variant={chip ? 'filled' : 'outlined'}
    />
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
        <CircularProgress size={22} aria-hidden="true" />
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

/** Inline error callout (MUI Alert), with optional retry — never a raw dump. */
export function ErrorBox({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <Alert
      role="alert"
      severity="error"
      className="[&>.MuiAlert-message]:w-full"
      action={
        onRetry ? (
          <Button
            color="inherit"
            size="small"
            startIcon={<RefreshCw className="h-3.5 w-3.5" aria-hidden="true" />}
            onClick={onRetry}
          >
            Try again
          </Button>
        ) : undefined
      }
    >
      {message}
    </Alert>
  );
}

/** Skeleton line for loading placeholders (MUI Skeleton). */
export function SkeletonLine({ className = 'h-4 w-full' }: { className?: string }) {
  return <Skeleton className={className} />;
}
export { SkeletonLine as Skeleton };

/** Small inline spinner for buttons and inline waits (MUI CircularProgress). */
export function Spinner({ className = '' }: { className?: string }) {
  // `inherit` makes the ring take the surrounding button's label colour — inside
  // a solid teal button that is white, without per-call-site colour classes.
  return <CircularProgress size={16} color="inherit" className={className} aria-hidden="true" />;
}
