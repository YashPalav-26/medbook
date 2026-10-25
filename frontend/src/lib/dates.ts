import { format, parseISO, isValid } from 'date-fns';

/**
 * date-fns based display formatting (Step 26 — no more hand-rolled date
 * strings). Date-only strings ("2026-09-28") parse as local midnight, which
 * is what the API's date fields represent.
 */

/**
 * Today as a local ISO date ("2026-10-25").
 *
 * `toISOString()` converts to UTC first, so between 00:00 and the UTC offset it
 * reports yesterday (or tomorrow) for the user — never use it for "today" in the UI.
 */
export function todayIso(): string {
  const now = new Date();
  return format(now, 'yyyy-MM-dd');
}

/** "2026-09-28" → "Mon, Sep 28, 2026" (falls back to the input if invalid). */
export function formatDate(iso: string): string {
  if (!iso) return iso;
  const date = parseISO(iso);
  if (!isValid(date)) return iso;
  return format(date, 'EEE, MMM d, yyyy');
}

/** "2026-09-21T09:30:00" → "Mon, Sep 21, 2026 · 09:30" (falls back if invalid). */
export function formatDateTime(iso: string): string {
  if (!iso) return iso;
  const date = parseISO(iso);
  if (!isValid(date)) return iso;
  return format(date, 'EEE, MMM d, yyyy · HH:mm');
}
