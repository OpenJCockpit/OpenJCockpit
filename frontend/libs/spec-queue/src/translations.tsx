import type { ReactNode } from 'react';
import { WithTranslations } from '@openjcockpit/i18n';
import en from './locales/en.json';

// English is bundled; every other language is its own chunk, downloaded when it is first needed.
const locales = import.meta.glob<{ default: Record<string, unknown> }>([
  './locales/*.json',
  '!**/en.json',
]);

/** Registers the SpecQueue namespace with the host's i18n instance; for use by a federated app. */
export function WithSpecQueueTranslations({ children }: { children: ReactNode }) {
  return (
    <WithTranslations namespace="specQueue" english={en.specQueue} locales={locales}>
      {children}
    </WithTranslations>
  );
}
