import type { ReactNode } from 'react';

export { StatusBadge, ActiveBadge, EmptyState, ErrorBox, Skeleton, Spinner } from '../ui/Feedback';

export function PageHeader({ icon, title, description }: { icon: ReactNode; title: string; description: string }) {
  return (
    <div className="mb-8 flex items-start gap-4">
      <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl bg-brand-50 text-brand-600">
        {icon}
      </div>
      <div className="min-w-0">
        <h1 className="font-display text-2xl font-semibold text-ink-900">{title}</h1>
        <p className="mt-1 text-ink-500">{description}</p>
      </div>
    </div>
  );
}
