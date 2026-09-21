import React from 'react';

interface CardProps extends React.HTMLAttributes<HTMLDivElement> {
  /** Adds hover elevation and pointer cursor — for clickable cards. */
  hover?: boolean;
}

/**
 * MedBook surface. White card on the ink-50 page background, radius-card,
 * shadow-card at rest and shadow-lift on hover when interactive.
 */
export function Card({ children, className = '', hover = false, ...props }: CardProps) {
  return (
    <div
      className={`rounded-card border border-ink-100 bg-white shadow-card ${
        hover ? 'cursor-pointer transition-shadow duration-200 hover:shadow-lift' : ''
      } ${className}`}
      {...props}
    >
      {children}
    </div>
  );
}
