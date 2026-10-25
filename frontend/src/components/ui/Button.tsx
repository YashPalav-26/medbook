import React from 'react';
import { Link } from 'react-router-dom';
import MuiButton, { type ButtonProps as MuiButtonProps } from '@mui/material/Button';

type MedBookVariant = 'primary' | 'secondary' | 'outline' | 'ghost' | 'danger';
type MedBookSize = 'sm' | 'md' | 'lg';

interface ButtonProps
  extends Omit<React.ButtonHTMLAttributes<HTMLButtonElement>, 'variant' | 'color'> {
  variant?: MedBookVariant;
  size?: MedBookSize;
  children: React.ReactNode;
}

/**
 * MedBook button — a thin adapter over MUI Button.
 *
 * MedBook variants map onto MUI's `color`/`variant` props, so the real
 * implementation (ripple, focus-visible, disabled semantics, keyboard
 * handling) comes from the third-party library while the call-site API stays
 * unchanged across the app.
 */
export function Button({
  variant = 'primary',
  size = 'md',
  className = '',
  children,
  ...props
}: ButtonProps) {
  const muiProps: MuiButtonProps & Record<string, unknown> = { ...props };

  switch (variant) {
    case 'primary':
      muiProps.variant = 'contained';
      muiProps.color = 'primary';
      break;
    case 'secondary':
      muiProps.variant = 'text';
      muiProps.color = 'secondary';
      break;
    case 'outline':
      muiProps.variant = 'outlined';
      muiProps.color = 'inherit';
      break;
    case 'ghost':
      muiProps.variant = 'text';
      muiProps.color = 'inherit';
      break;
    case 'danger':
      muiProps.variant = 'contained';
      muiProps.color = 'error';
      break;
  }

  const muiSize = size === 'sm' ? 'small' : size === 'lg' ? 'large' : 'medium';

  return (
    <MuiButton size={muiSize} className={className} {...muiProps}>
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

  switch (variant) {
    case 'primary':
      muiProps.variant = 'contained';
      muiProps.color = 'primary';
      break;
    case 'secondary':
      muiProps.variant = 'text';
      muiProps.color = 'secondary';
      break;
    case 'outline':
      muiProps.variant = 'outlined';
      muiProps.color = 'inherit';
      break;
    case 'ghost':
      muiProps.variant = 'text';
      muiProps.color = 'inherit';
      break;
    case 'danger':
      muiProps.variant = 'contained';
      muiProps.color = 'error';
      break;
  }

  const muiSize = size === 'sm' ? 'small' : size === 'lg' ? 'large' : 'medium';

  return (
    <MuiButton
      component={Link}
      to={to}
      size={muiSize}
      className={className}
      {...(muiProps as object)}
    >
      {children}
    </MuiButton>
  );
}
