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

  to: string;
}

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
