import { renderWithI18n } from '@openjcockpit/i18n/testing';
import type { ReactElement } from 'react';
import { dashboardResources } from '../i18n';

/** Renders `ui` with the app's English resources, so component tests assert the visible text. */
export function render(ui: ReactElement) {
  return renderWithI18n(ui, { resources: dashboardResources });
}
