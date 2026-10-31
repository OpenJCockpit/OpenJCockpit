import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { pseudoResources, withI18n } from '@openjcockpit/i18n/testing';
import type { ReactElement } from 'react';
import { apiResources } from '@openjcockpit/api';
import { mergeResources } from '@openjcockpit/i18n';
import { workflowResources } from '../i18n';

/**
 * Wraps `ui` in English i18n (pseudo-localized with `pseudo`) and a fresh query cache with retries
 * off, so a mocked rejection surfaces at once.
 */
export function withQueryClient(ui: ReactElement, { pseudo = false } = {}) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } });
  const resources = mergeResources(apiResources, workflowResources);
  return withI18n(
    <QueryClientProvider client={client}>{ui}</QueryClientProvider>,
    pseudo ? pseudoResources(resources) : resources,
  );
}
