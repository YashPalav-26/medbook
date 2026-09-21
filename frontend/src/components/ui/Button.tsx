import React from 'react';

interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'outline' | 'ghost' | 'danger';
  size?: 'sm' | 'md' | 'lg';
  children: React.ReactNode;
}

/**
 * MedBook button.
 *
 * Tokens: brand-600 primary, ink-100 secondary, outline/ghost for low-emphasis
 * actions, danger for destructive ones. All variants share radius-field,
 * shadow-card and the same focus treatment. Disabled state keeps contrast and
 * shows the not-allowed cursor.
 */
export function Button({
  variant = 'primary',
  size = 'md',
  className = '',
  children,
  ...props
}: ButtonProps) {
  const base =
    'inline-flex items-center justify-center gap-2 rounded-field font-medium transition-all duration-150 ' +
    'disabled:cursor-not-allowed disabled:opacity-60';

  const variants: Record<string, string> = {
    primary: 'bg-brand-600 text-white shadow-card hover:bg-brand-700 active:bg-brand-800',
    secondary: 'bg-ink-100 text-ink-800 hover:bg-ink-200',
    outline: 'border border-ink-200 bg-white text-ink-800 shadow-card hover:border-brand-300 hover:text-brand-700',
    ghost: 'text-ink-600 hover:bg-ink-100 hover:text-ink-900',
    danger: 'bg-danger-600 text-white shadow-card hover:bg-danger-700',
  };

  const sizes: Record<string, string> = {
    sm: 'px-3 py-1.5 text-sm',
    md: 'px-4 py-2 text-sm',
    lg: 'px-5 py-2.5 text-base',
  };

  return (
    <button className={`${base} ${variants[variant]} ${sizes[size]} ${className}`} {...props}>
      {children}
    </button>
  );
}
