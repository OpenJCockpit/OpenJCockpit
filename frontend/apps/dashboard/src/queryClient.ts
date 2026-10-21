import { QueryClient } from '@tanstack/react-query';

export function createQueryClient(overrides: { retry?: number | false } = {}) {
  return new QueryClient({
    defaultOptions: {
      queries: { staleTime: 30_000, retry: overrides.retry ?? 1 },
    },
  });
}
