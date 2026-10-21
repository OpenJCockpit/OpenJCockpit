import { act, render } from '@testing-library/react';
import { vi } from 'vitest';
import type { ReactElement } from 'react';
import { withQueryClient } from './testQueryClient';

/** Renders inside `act` so effects and their resolved mocks settle before assertions. */
export async function renderAsync(ui: ReactElement) {
  await act(async () => {
    render(withQueryClient(ui));
  });
}

/** Clicks inside `act` so state updates flush before assertions. */
export async function clickAsync(element: HTMLElement) {
  await act(async () => {
    element.click();
  });
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
