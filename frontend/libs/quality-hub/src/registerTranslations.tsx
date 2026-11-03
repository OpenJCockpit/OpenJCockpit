import type { ReactNode } from 'react';
import { WithTranslations as RegisterNamespace } from '@openjcockpit/i18n';
import en from './locales/en.json';

// English is bundled; every other language is its own chunk, downloaded when it is first needed.
const locales = import.meta.glob<{ default: Record<string, unknown> }>([
  './locales/*.json',
  '!**/en.json',
]);

/** Registers the Quality namespace with the host's i18n instance. */
export function WithTranslations({ children }: { children: ReactNode }) {
  return (
    <RegisterNamespace namespace="quality" english={en.quality} locales={locales}>
      {children}
    </RegisterNamespace>
  );
}
