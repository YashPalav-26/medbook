import React from 'react';
import { Link } from 'react-router-dom';

interface CardProps extends React.HTMLAttributes<HTMLDivElement> {

  hover?: boolean;
}

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

export function LinkCard({
  to,
  children,
  className = '',
  label,
}: {
  to: string;
  children: React.ReactNode;
  className?: string;

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
