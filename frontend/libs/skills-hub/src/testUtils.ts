import { act, render } from '@testing-library/react';
import { vi } from 'vitest';
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

type ApiModule = typeof import('@openjcockpit/api');

/** Replaces every HTTP client of `@openjcockpit/api` with `vi.fn()`; constants and types stay real. */
export function automockApiClients(actual: ApiModule): ApiModule {
  return Object.fromEntries(
    Object.entries(actual).map(([name, value]) => [
      name,
      typeof value === 'function' && name !== 'errorMessageFrom' ? vi.fn() : value,
    ]),
  ) as ApiModule;
}
