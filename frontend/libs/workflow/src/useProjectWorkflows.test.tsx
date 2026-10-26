import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as api from '@openjcockpit/api';
import type { WorkflowDefinition } from '@openjcockpit/api';
import { useProjectWorkflows } from './useProjectWorkflows';

vi.mock('@openjcockpit/api', async (importOriginal) =>
  (await import('./testUtils/mockApi')).automockApiClients(await importOriginal()),
);

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

const wf = (id: string, extra: Partial<WorkflowDefinition> = {}) =>
  ({ id, name: id, ...extra }) as WorkflowDefinition;

describe('useProjectWorkflows', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(api.listWorkflows).mockResolvedValue([
      wf('own', { projectName: 'Acme' }),
      wf('other', { projectName: 'Other' }),
      wf('global'),
      wf('grouped', { groupId: 'g1' }),
    ]);
    vi.mocked(api.listWorkflowGroups).mockResolvedValue([
      { id: 'g1', name: 'G', description: '', projectName: 'Acme' },
    ]);
  });

  it('returns the project’s own, grouped and global workflows', async () => {
    const { result } = renderHook(() => useProjectWorkflows('Acme'), { wrapper });
    await waitFor(() => expect(result.current.map((w) => w.id)).toContain('own'));
    expect(result.current.map((w) => w.id).sort()).toEqual(['global', 'grouped', 'own']);
  });

  it('returns everything when no project is given', async () => {
    const { result } = renderHook(() => useProjectWorkflows(undefined), { wrapper });
    await waitFor(() => expect(result.current).toHaveLength(4));
  });

  it('does not fetch while disabled', () => {
    const { result } = renderHook(() => useProjectWorkflows('Acme', false), { wrapper });
    expect(result.current).toEqual([]);
    expect(api.listWorkflows).not.toHaveBeenCalled();
    expect(api.listWorkflowGroups).not.toHaveBeenCalled();
  });

  it('yields an empty list when the workflows fail to load', async () => {
    vi.mocked(api.listWorkflows).mockRejectedValue(new Error('x'));
    const { result } = renderHook(() => useProjectWorkflows('Acme'), { wrapper });
    await waitFor(() => expect(api.listWorkflows).toHaveBeenCalled());
    expect(result.current).toEqual([]);
  });
});
