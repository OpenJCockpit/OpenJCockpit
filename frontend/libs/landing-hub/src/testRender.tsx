import { renderWithI18n } from '@openjcockpit/i18n/testing';
import type { ReactElement } from 'react';
import { landingResources } from './i18n';

/** Renders `ui` with the hub's English resources, so tests assert the visible text. */
export function render(ui: ReactElement) {
  return renderWithI18n(ui, { resources: landingResources });
}
