import { render, type RenderOptions } from '@testing-library/react';
import type { Resource } from 'i18next';
import type { ReactElement } from 'react';
import { createI18n } from '../createI18n';
import { I18nProvider } from '../I18nProvider';

/** Wraps `ui` in an English-only provider built from the real `en` resources, so tests assert visible text. */
export function withI18n(ui: ReactElement, resources?: Resource): ReactElement {
  return <I18nProvider i18n={createI18n({ resources })}>{ui}</I18nProvider>;
}

export function renderWithI18n(
  ui: ReactElement,
  options?: RenderOptions & { resources?: Resource },
) {
  const { resources, ...renderOptions } = options ?? {};
  return render(withI18n(ui, resources), renderOptions);
}
