import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as api from '@openjcockpit/api';
import { projectsQueryKey, useProjectsQuery } from './useProjectsQuery';

vi.mock('@openjcockpit/api', async (importOriginal) =>
  (await import('./testUtils')).automockApiClients(await importOriginal()),
);

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

describe('useProjectsQuery', () => {
  beforeEach(() => vi.clearAllMocks());

  it('uses the shared query key', () => {
    expect(projectsQueryKey).toEqual(['projects']);
  });

  it('loads the active projects, passing the abort signal', async () => {
    vi.mocked(api.loadProjects).mockResolvedValue([{ id: 'p1' } as api.Project]);
    const { result } = renderHook(() => useProjectsQuery(), { wrapper });
    await waitFor(() => expect(result.current.data).toEqual([{ id: 'p1' }]));
    expect(api.loadProjects).toHaveBeenCalledWith(expect.any(AbortSignal));
  });

  it('does not fetch while disabled', () => {
    renderHook(() => useProjectsQuery(false), { wrapper });
    expect(api.loadProjects).not.toHaveBeenCalled();
  });

  it('exposes a failed load as an error', async () => {
    vi.mocked(api.loadProjects).mockRejectedValue(new Error('boom'));
    const { result } = renderHook(() => useProjectsQuery(), { wrapper });
    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});
