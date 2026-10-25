/**
 * Display formatting shared by every page.
 *
 * Fees are stored as plain decimals and displayed in one currency everywhere;
 * having a single helper prevents the mix of `$` labels and non-dollar icons
 * that crept into the appointment screens.
 */

const currency = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD',
  minimumFractionDigits: 2,
});

/** 320 → "$320.00" */
export function formatCurrency(amount: number | null | undefined): string {
  if (amount == null || Number.isNaN(amount)) return '—';
  return currency.format(amount);
}
