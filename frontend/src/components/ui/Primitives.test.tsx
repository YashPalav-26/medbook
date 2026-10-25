import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect } from 'vitest';
import { ButtonLink } from './Button';
import { LinkCard } from './Card';
import { Input } from './Input';
import { formatCurrency } from '../../lib/format';

describe('navigation primitives', () => {
  it('ButtonLink renders one anchor and no nested button', () => {
    render(
      <MemoryRouter>
        <ButtonLink to="/appointments/new">Book appointment</ButtonLink>
      </MemoryRouter>,
    );

    const link = screen.getByRole('link', { name: 'Book appointment' });
    expect(link).toHaveAttribute('href', '/appointments/new');

    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });

  it('LinkCard is a keyboard-focusable link with an accessible name', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <LinkCard to="/appointments/42" label="Appointment 42">
          <p>Dr. Sarah Wells</p>
        </LinkCard>
      </MemoryRouter>,
    );

    const link = screen.getByRole('link', { name: 'Appointment 42' });
    expect(link).toHaveAttribute('href', '/appointments/42');

    await user.tab();
    expect(link).toHaveFocus();
  });
});

describe('Input', () => {
  it('gives react-hook-form’s ref the real input element, not the wrapper', () => {
    let received: HTMLElement | null = null;

    render(
      <Input
        label="Email"
        name="email"
        ref={(element) => {
          received = element;
        }}
      />,
    );

    expect(received).toBeInstanceOf(HTMLInputElement);
    expect(received).toBe(screen.getByLabelText('Email'));
  });
});

describe('formatCurrency', () => {
  it('formats fees in one currency with two decimals', () => {
    expect(formatCurrency(320)).toBe('$320.00');
    expect(formatCurrency(0)).toBe('$0.00');
    expect(formatCurrency(null)).toBe('—');
  });
});
