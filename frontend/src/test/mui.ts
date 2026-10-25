import { fireEvent, waitFor } from '@testing-library/react';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect } from 'vitest';














type Scope = {
  getByLabelText: (text: string) => HTMLElement;
};


export async function waitForEnabled(el: HTMLElement): Promise<void> {
  await waitFor(() => expect(el.getAttribute('aria-disabled')).not.toBe('true'));
}









export async function pickFromSelect(
  user: ReturnType<typeof userEvent.setup>,
  from: Scope,
  label: string,
  option: string | RegExp,
): Promise<void> {
  const trigger = from.getByLabelText(label);
  await waitForEnabled(trigger);
  fireEvent.mouseDown(trigger);
  await user.click(await screen.findByRole('option', { name: option }));
}
