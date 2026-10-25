import { toast as sonnerToast } from 'sonner';

/**
 * Thin facade over Sonner (the installed third-party toast library).
 *
 * MedBook call sites use `toast.success('…') / toast.error('…') / toast.info('…')`
 * — the same tiny API the previous in-house ToastProvider exposed — but the
 * rendering, stacking, a11y live region and dismissal now come from Sonner's
 * `<Toaster>` mounted once in `App.tsx`. No custom toast UI is maintained here.
 */
export const toast = {
  success: (message: string) => sonnerToast.success(message),
  error: (message: string) => sonnerToast.error(message),
  info: (message: string) => sonnerToast(message),
};
