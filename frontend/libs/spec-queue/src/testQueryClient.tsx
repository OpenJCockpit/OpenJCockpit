import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { withI18n } from '@openjcockpit/i18n/testing';
import type { ReactElement } from 'react';
import { specQueueResources } from './i18n';

/** Wraps `ui` in English i18n and a fresh query cache with retries off, so a mocked rejection surfaces at once. */
export function withQueryClient(ui: ReactElement) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } });
  return withI18n(
    <QueryClientProvider client={client}>{ui}</QueryClientProvider>,
    specQueueResources,
  );
}
