import React from 'react';

interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  label: string;
  /** Optional helper line shown under the label. */
  helper?: string;
  error?: string;
}

/**
 * MedBook text field. Label + helper sit above the control (helps screen-reader
 * and label-click users alike); the control uses radius-field, an ink-200 border
 * that turns brand on focus and danger on error. The label is always programmatically
 * associated (htmlFor/id).
 */
export function Input({ label, helper, error, className = '', id, ...props }: InputProps) {
  const generatedId = React.useId();
  const inputId = id ?? generatedId;

  return (
    <div className={className}>
      <label htmlFor={inputId} className="mb-1 block text-sm font-medium text-ink-700">
        {label}
      </label>
      {helper && <p className="mb-1 text-xs text-ink-500">{helper}</p>}
      <input
        id={inputId}
        aria-invalid={error ? true : undefined}
        className={`w-full rounded-field border bg-white px-3 py-2 text-ink-900 placeholder:text-ink-400 transition-colors focus:outline-none focus:ring-2 focus:ring-brand-500/40 focus:border-brand-500 ${
          error ? 'border-danger-600' : 'border-ink-200'
        }`}
        {...props}
      />
      {error && <p className="mt-1 text-sm text-danger-700">{error}</p>}
    </div>
  );
}
