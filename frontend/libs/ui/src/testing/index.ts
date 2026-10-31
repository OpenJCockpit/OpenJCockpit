import { fireEvent, within } from '@testing-library/react';

function openListbox(trigger: HTMLElement): HTMLElement {
  fireEvent.click(trigger);
  const list = document.getElementById(trigger.getAttribute('aria-controls') ?? '');
  if (!list) throw new Error('Select did not open (is it disabled?)');
  return list;
}

/** Opens a `Select` trigger and picks the option with the given label. */
export function chooseOption(trigger: HTMLElement, name: string | RegExp): void {
  fireEvent.click(within(openListbox(trigger)).getByRole('option', { name }));
}

/** Labels of every option of a `Select`; leaves the list closed afterwards. */
export function optionLabels(trigger: HTMLElement): string[] {
  const labels = within(openListbox(trigger))
    .getAllByRole('option')
    .map((o) => o.textContent);
  fireEvent.keyDown(trigger, { key: 'Escape' });
  return labels;
}
