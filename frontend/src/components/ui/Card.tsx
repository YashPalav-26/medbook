import React from 'react';
import { Link } from 'react-router-dom';

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

/**
 * A card that navigates when activated.
 *
 * Rendered as a real `<Link>` (not a div with onClick) so it is reachable with
 * Tab, activated with Enter, listed as a link by screen readers, and supports
 * middle-click / open-in-new-tab. The focus ring sits on the link, the surface
 * stays a Card.
 */
export function LinkCard({
  to,
  children,
  className = '',
  label,
}: {
  to: string;
  children: React.ReactNode;
  className?: string;
  /** Accessible name when the card's visible text is not descriptive enough. */
  label?: string;
}) {
  return (
    <Link
      to={to}
      aria-label={label}
      className="block rounded-card focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:ring-offset-2"
    >
      <Card hover className={className}>
        {children}
      </Card>
    </Link>
  );
}
