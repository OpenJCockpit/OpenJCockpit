import { act, render, screen } from '@testing-library/react';
import type { ReactElement } from 'react';

/** Renders inside `act` so effects and their resolved mocks settle before assertions. */
export async function renderAsync(ui: ReactElement) {
  await act(async () => {
    render(ui);
  });
}

/** Clicks inside `act` so state updates flush before assertions. */
export async function clickAsync(element: HTMLElement) {
  await act(async () => {
    element.click();
  });
}

/** Sets a controlled input/textarea value the way React's onChange expects. */
export function setInputValue(element: HTMLInputElement | HTMLTextAreaElement, value: string) {
  const prototype =
    element instanceof HTMLTextAreaElement
      ? HTMLTextAreaElement.prototype
      : HTMLInputElement.prototype;
  const setter = Object.getOwnPropertyDescriptor(prototype, 'value')?.set;
  setter?.call(element, value);
  element.dispatchEvent(new Event('input', { bubbles: true }));
}

/** Passes the post-login hub by choosing the Implementation portal. */
export async function enterDashboard() {
  await clickAsync(await screen.findByTestId('portal-orb-implementation'));
}
