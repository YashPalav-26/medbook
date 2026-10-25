import { fireEvent, waitFor } from '@testing-library/react';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect } from 'vitest';

/**
 * Interaction helpers for MUI Selects in tests.
 *
 * MUI Selects are not native <select> elements, so `user.selectOptions` and
 * `fireEvent.change` do not apply. The supported interaction is: wait until the
 * trigger is enabled, open the listbox with a mousedown, then click the option.
 *
 * Note: MUI v9 conveys the disabled state with `aria-disabled="true"` on the
 * combobox trigger; jest-dom's `toBeDisabled` matcher does not inspect that
 * attribute, so helpers must check it explicitly. MUI ignores mousedown on a
 * disabled trigger, which would otherwise hang the option lookup.
 */

type Scope = {
  getByLabelText: (text: string) => HTMLElement;
};

/** Wait until an element is not aria-disabled (MUI v9 disabled semantics). */
export async function waitForEnabled(el: HTMLElement): Promise<void> {
  await waitFor(() => expect(el.getAttribute('aria-disabled')).not.toBe('true'));
}

/**
 * Open the labeled MUI Select and click the given option.
 *
 * `from` is the query scope for the trigger — `screen` for plain pages or a
 * `within(dialog)` result when a select lives inside a modal. Options are
 * always searched at screen level: MUI renders the open listbox in a
 * body-level portal, outside any dialog subtree.
 */
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
