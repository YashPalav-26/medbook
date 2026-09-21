import type { ReactNode } from 'react';

// Admin pages share the core feedback primitives with the rest of the app.
export { StatusBadge, ActiveBadge, EmptyState, ErrorBox, Skeleton, Spinner } from '../ui/Feedback';

/**
 * Page heading block with icon, title and description — the same header pattern
 * every page uses (patient and admin alike).
 */
export function PageHeader({ icon, title, description }: { icon: ReactNode; title: string; description: string }) {
  return (
    <div className="mb-8">
      <h1 className="font-display text-2xl font-semibold text-ink-900 flex items-center gap-3">
        {icon}
        {title}
      </h1>
      <p className="mt-1 text-ink-500">{description}</p>
    </div>
  );
}
