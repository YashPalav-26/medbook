import { format, parseISO, isValid } from 'date-fns';













export function todayIso(): string {
  const now = new Date();
  return format(now, 'yyyy-MM-dd');
}


export function formatDate(iso: string): string {
  if (!iso) return iso;
  const date = parseISO(iso);
  if (!isValid(date)) return iso;
  return format(date, 'EEE, MMM d, yyyy');
}


export function formatDateTime(iso: string): string {
  if (!iso) return iso;
  const date = parseISO(iso);
  if (!isValid(date)) return iso;
  return format(date, 'EEE, MMM d, yyyy · HH:mm');
}
