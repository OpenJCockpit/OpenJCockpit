import type { ReactNode } from 'react';
import { WithTranslations as RegisterNamespace } from '@openjcockpit/i18n';
import en from '../locales/en.json';

// English is bundled; every other language is its own chunk, downloaded when it is first needed.
const locales = import.meta.glob<{ default: Record<string, unknown> }>([
  '../locales/*.json',
  '!**/en.json',
]);

/** Registers the namespace the widgets and top bar items of this remote use; they load lazily. */
export function WithContributionTranslations({ children }: { children: ReactNode }) {
  return (
    <RegisterNamespace
      namespace="specsContributions"
      english={en.specsContributions}
      locales={locales}
    >
      {children}
    </RegisterNamespace>
  );
}
