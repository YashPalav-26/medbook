import { createContext, useCallback, useContext, useMemo, useState } from 'react';
import { CheckCircle2, AlertCircle, Info, X } from 'lucide-react';

/**
 * Minimal app-wide toast system — no external dependency.
 *
 * Usage: const toast = useToast(); toast.success('Specialty created');
 * The provider holds up to 4 toasts; each auto-dismisses (errors linger longer),
 * and respects reduced motion via the global CSS rule.
 */

type ToastKind = 'success' | 'error' | 'info';

interface ToastItem {
  id: number;
  kind: ToastKind;
  message: string;
}

interface ToastContextValue {
  success: (message: string) => void;
  error: (message: string) => void;
  info: (message: string) => void;
}

const ToastContext = createContext<ToastContextValue | null>(null);

let nextId = 1;

export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([]);

  const push = useCallback((kind: ToastKind, message: string) => {
    const id = nextId++;
    setToasts((current) => [...current.slice(-3), { id, kind, message }]);
    window.setTimeout(() => {
      setToasts((current) => current.filter((t) => t.id !== id));
    }, kind === 'error' ? 6000 : 3500);
  }, []);

  const dismiss = useCallback((id: number) => {
    setToasts((current) => current.filter((t) => t.id !== id));
  }, []);

  const value = useMemo<ToastContextValue>(
    () => ({
      success: (m) => push('success', m),
      error: (m) => push('error', m),
      info: (m) => push('info', m),
    }),
    [push],
  );

  const icons: Record<ToastKind, React.ReactNode> = {
    success: <CheckCircle2 className="h-5 w-5 text-success-700" aria-hidden="true" />,
    error: <AlertCircle className="h-5 w-5 text-danger-700" aria-hidden="true" />,
    info: <Info className="h-5 w-5 text-info-700" aria-hidden="true" />,
  };

  return (
    <ToastContext.Provider value={value}>
      {children}
      {/* Live region announces toasts to screen readers */}
      <div aria-live="polite" className="pointer-events-none fixed inset-x-4 bottom-4 z-[60] flex flex-col items-center gap-2 sm:items-end sm:right-6">
        {toasts.map((toast) => (
          <div
            key={toast.id}
            role="status"
            className="animate-rise pointer-events-auto flex w-full max-w-sm items-start gap-3 rounded-card border border-ink-100 bg-white p-4 shadow-modal"
          >
            {icons[toast.kind]}
            <p className="flex-1 text-sm text-ink-800">{toast.message}</p>
            <button
              type="button"
              onClick={() => dismiss(toast.id)}
              aria-label="Dismiss notification"
              className="text-ink-400 hover:text-ink-600"
            >
              <X className="h-4 w-4" />
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast(): ToastContextValue {
  const ctx = useContext(ToastContext);
  if (!ctx) {
    // Fail soft: missing provider should never crash a page.
    return { success: () => {}, error: () => {}, info: () => {} };
  }
  return ctx;
}
