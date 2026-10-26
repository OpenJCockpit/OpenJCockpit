import { describe, expect, it } from 'vitest';
import { ApiError } from '@openjcockpit/api';
import { createQueryClient } from './queryClient';

function retryFn(client = createQueryClient()) {
  return client.getDefaultOptions().queries!.retry as (n: number, e: unknown) => boolean;
}

describe('createQueryClient', () => {
  it('retries a network error once', () => {
    const retry = retryFn();
    expect(retry(0, new TypeError('Failed to fetch'))).toBe(true);
    expect(retry(1, new TypeError('Failed to fetch'))).toBe(false);
  });

  it('never retries a response from the server', () => {
    expect(retryFn()(0, new ApiError('bad gateway', 502))).toBe(false);
  });

  it('lets the caller override the retry policy', () => {
    expect(createQueryClient({ retry: false }).getDefaultOptions().queries!.retry).toBe(false);
    expect(createQueryClient({ retry: 3 }).getDefaultOptions().queries!.retry).toBe(3);
  });

  it('treats data as fresh for 30 seconds', () => {
    expect(createQueryClient().getDefaultOptions().queries!.staleTime).toBe(30_000);
  });
});
