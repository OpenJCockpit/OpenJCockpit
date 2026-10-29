import { renderWithI18n } from '@openjcockpit/i18n/testing';
import type { ReactElement } from 'react';
import { projectResources } from './i18n';

/** Renders `ui` under English i18n with this lib's real resources, so tests assert visible text. */
export function render(ui: ReactElement) {
  return renderWithI18n(ui, { resources: projectResources });
}
