import { Link } from 'react-router-dom';

/**
 * MedBook brand wordmark.
 *
 * The glyph is a rounded square carrying a cross-and-bookmark mark — a clinical
 * cross whose stem ends in a bookmark, for "booking". Drawn inline (no image
 * assets), sized by font size so it scales from sidebar to mobile header.
 * `to` renders it as a link; omit for a static mark (e.g. inside the sidebar).
 */
export function Brand({
  to,
  compact = false,
  onClick,
}: {
  to?: string;
  compact?: boolean;
  /** Optional click handler — the mobile drawer uses it to close itself. */
  onClick?: () => void;
}) {
  const mark = (
    <span className="inline-flex items-center gap-2.5">
      <span
        aria-hidden="true"
        className="inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-brand-600 shadow-card"
      >
        <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="white" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
          {/* bookmark outline */}
          <path d="M7 3.5h10a1 1 0 0 1 1 1v16l-6-3.4L6 20.5v-16a1 1 0 0 1 1-1Z" />
          {/* clinical cross */}
          <path d="M12 7.5v5M9.5 10h5" />
        </svg>
      </span>
      {!compact && (
        <span className="font-display text-xl font-semibold tracking-tight text-ink-900">
          Med<span className="text-brand-600">Book</span>
        </span>
      )}
    </span>
  );

  if (to) {
    return (
      <Link to={to} onClick={onClick} aria-label="MedBook home" className="inline-flex items-center rounded-field">
        {mark}
      </Link>
    );
  }
  return mark;
}
