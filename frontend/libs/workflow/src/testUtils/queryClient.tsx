import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactElement } from 'react';

/** Wraps `ui` in a fresh query cache with retries off, so a mocked rejection surfaces at once. */
export function withQueryClient(ui: ReactElement) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } });
  return <QueryClientProvider client={client}>{ui}</QueryClientProvider>;
}
