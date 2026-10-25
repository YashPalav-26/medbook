import React from 'react';
import { Link } from 'react-router-dom';
import MuiButton, { type ButtonProps as MuiButtonProps } from '@mui/material/Button';

type MedBookVariant =
  | 'primary'
  | 'secondary'
  | 'outline'
  | 'ghost'
  | 'danger'
  /** Danger-tinted outline — cancel/irreversible actions. */
  | 'dangerOutline'
  /** Solid white action for the brand-coloured CTA panel. */
  | 'onBrand'
  /** Ghost action for the brand-coloured CTA panel (white label). */
  | 'onBrandGhost';
type MedBookSize = 'sm' | 'md' | 'lg';

interface ButtonProps
  extends Omit<React.ButtonHTMLAttributes<HTMLButtonElement>, 'variant' | 'color'> {
  variant?: MedBookVariant;
  size?: MedBookSize;
  children: React.ReactNode;
}

/** One mapping for both Button and ButtonLink, so the two can never diverge. */
function applyVariant(variant: MedBookVariant, target: MuiButtonProps & Record<string, unknown>) {
  switch (variant) {
    case 'primary':
      target.variant = 'contained';
      target.color = 'primary';
      break;
    case 'secondary':
      target.variant = 'text';
      target.color = 'secondary';
      break;
    case 'outline':
      target.variant = 'outlined';
      target.color = 'inherit';
      break;
    case 'ghost':
      target.variant = 'text';
      target.color = 'inherit';
      break;
    case 'danger':
      target.variant = 'contained';
      target.color = 'error';
      break;
    case 'dangerOutline':
      target.variant = 'outlined';
      target.color = 'error';
      break;
    case 'onBrand':
      // `info` is the palette key MUI accepts; the theme's variant for this
      // combination renders white-on-brand, never blue.
      target.variant = 'contained';
      target.color = 'info';
      break;
    case 'onBrandGhost':
      target.variant = 'text';
      target.color = 'info';
      break;
  }
}

function muiSizeOf(size: MedBookSize) {
  return size === 'sm' ? 'small' : size === 'lg' ? 'large' : 'medium';
}

/**
 * MedBook button — a thin adapter over MUI Button.
 *
 * MedBook variants map onto MUI's `color`/`variant` props, so the real
 * implementation (ripple, focus-visible, disabled semantics, keyboard
 * handling) comes from the third-party library while the call-site API stays
 * unchanged across the app. The resting and hover colours live in the theme's
 * `variants` — never as Tailwind classes on the element, because MUI's
 * (unlayered) rules outrank Tailwind's utilities layer.
 */
export function Button({
  variant = 'primary',
  size = 'md',
  className = '',
  children,
  ...props
}: ButtonProps) {
  const muiProps: MuiButtonProps & Record<string, unknown> = { ...props };
  applyVariant(variant, muiProps);

  return (
    <MuiButton size={muiSizeOf(size)} className={className} {...muiProps}>
      {children}
    </MuiButton>
  );
}

interface ButtonLinkProps extends Omit<ButtonProps, 'type' | 'onClick'> {
  /** Router destination — rendered as a real anchor, never a button. */
  to: string;
}

/**
 * Navigation styled as a MedBook button.
 *
 * Use this instead of wrapping `<Button>` in `<Link>`: an anchor may not contain
 * a button (invalid HTML, and keyboard/screen-reader users get two conflicting
 * controls). This renders a single MUI-styled anchor through React Router, so
 * middle-click, "open in new tab" and focus behaviour stay intact.
 */
export function ButtonLink({ to, variant = 'primary', size = 'md', className = '', children, ...props }: ButtonLinkProps) {
  const muiProps: MuiButtonProps & Record<string, unknown> = { ...props };
  applyVariant(variant, muiProps);

  return (
    <MuiButton
      component={Link}
      to={to}
      size={muiSizeOf(size)}
      className={className}
      {...(muiProps as object)}
    >
      {children}
    </MuiButton>
  );
}
