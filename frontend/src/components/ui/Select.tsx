import React from 'react';

interface SelectProps extends React.SelectHTMLAttributes<HTMLSelectElement> {
  label: string;
  helper?: string;
  error?: string;
}

/**
 * MedBook select field, mirroring Input: same radius, borders, focus ring and
 * error treatment. Chevron is decorative (the native one remains for interaction).
 */
export function Select({ label, helper, error, className = '', id, children, ...props }: SelectProps) {
  const generatedId = React.useId();
  const selectId = id ?? generatedId;

  return (
    <div className={className}>
      <label htmlFor={selectId} className="mb-1 block text-sm font-medium text-ink-700">
        {label}
      </label>
      {helper && <p className="mb-1 text-xs text-ink-500">{helper}</p>}
      <select
        id={selectId}
        aria-invalid={error ? true : undefined}
        className={`w-full rounded-field border bg-white px-3 py-2 text-ink-900 transition-colors focus:outline-none focus:ring-2 focus:ring-brand-500/40 focus:border-brand-500 ${
          error ? 'border-danger-600' : 'border-ink-200'
        }`}
        {...props}
      >
        {children}
      </select>
      {error && <p className="mt-1 text-sm text-danger-700">{error}</p>}
    </div>
  );
}
