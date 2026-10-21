import { QueryClient } from '@tanstack/react-query';
import { ApiError } from '@openjcockpit/api';

const MAX_NETWORK_RETRIES = 1;

// A response from the server (e.g. a 502 for an unreachable git repository) fails the same way
// on every attempt, so only network errors are worth retrying.
function retryNetworkErrors(failureCount: number, error: unknown) {
  return !(error instanceof ApiError) && failureCount < MAX_NETWORK_RETRIES;
}

export function createQueryClient(overrides: { retry?: number | false } = {}) {
  return new QueryClient({
    defaultOptions: {
      queries: { staleTime: 30_000, retry: overrides.retry ?? retryNetworkErrors },
    },
  });
}
