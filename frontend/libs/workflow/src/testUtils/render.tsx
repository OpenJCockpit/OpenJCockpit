import { render as rtlRender, type RenderOptions } from '@testing-library/react';
import type { ReactElement, ReactNode } from 'react';
import { apiResources } from '@openjcockpit/api';
import { mergeResources } from '@openjcockpit/i18n';
import { withI18n } from '@openjcockpit/i18n/testing';
import { workflowResources } from '../i18n';

const resources = mergeResources(apiResources, workflowResources);

function I18nWrapper({ children }: { children: ReactNode }) {
  return withI18n(<>{children}</>, resources);
}

/** `render` inside an English i18n provider with this lib's real resources; `rerender` keeps the provider. */
export function render(ui: ReactElement, options?: Omit<RenderOptions, 'wrapper'>) {
  return rtlRender(ui, { wrapper: I18nWrapper, ...options });
}
