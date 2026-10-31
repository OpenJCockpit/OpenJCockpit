import { act, renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { describe, expect, it, vi } from 'vitest';
import type { SpecQueueItem } from '@openjcockpit/api';
import { withI18n } from '@openjcockpit/i18n/testing';
import { specQueueResources } from '../i18n';
import { useQueueActions } from './useQueueActions';

const api = vi.hoisted(() => ({
  pauseSpecQueue: vi.fn(),
  removeSpecQueueItem: vi.fn(),
  reorderSpecQueue: vi.fn(),
  resumeSpecQueue: vi.fn(),
  retrySpecQueueItem: vi.fn(),
  skipSpecQueueItem: vi.fn(),
  updateSpecQueueItem: vi.fn(),
}));
vi.mock('@openjcockpit/api', () => api);

const item = { id: 'a', specFile: 'a.md', workflowId: 'w1' } as SpecQueueItem;

function setup() {
  const client = new QueryClient({ defaultOptions: { mutations: { retry: false } } });
  const wrapper = ({ children }: { children: ReactNode }) =>
    withI18n(
      <QueryClientProvider client={client}>{children}</QueryClientProvider>,
      specQueueResources,
    );
  const refresh = vi.fn().mockResolvedValue(undefined);
  const announce = vi.fn();
  const hook = renderHook(
    () =>
      useQueueActions({
        projectId: null,
        queue: null,
        queuedItems: [item],
        listRef: { current: null },
        applyQueue: vi.fn(),
        refresh,
        announce,
      }),
    { wrapper },
  );
  return { ...hook, refresh, announce };
}

describe('useQueueActions without a project', () => {
  it('ignores queue actions', () => {
    const { result } = setup();

    act(() => {
      result.current.move(item, 'down');
      result.current.pauseResume(true);
      result.current.setAutoMerge(item, true);
      result.current.retry(item);
    });

    Object.values(api).forEach((fn) => expect(fn).not.toHaveBeenCalled());
    expect(result.current.busy).toBe(false);
  });

  it('ignores confirm when nothing is awaiting confirmation', () => {
    const { result, announce } = setup();

    act(() => result.current.confirm());

    expect(result.current.confirmBusy).toBe(false);
    expect(announce).not.toHaveBeenCalled();
  });

  it('reports a confirmed action as failed', async () => {
    const { result } = setup();

    act(() => result.current.openConfirmation('skip', item));
    act(() => result.current.confirm());

    await waitFor(() => expect(result.current.confirmError).toBe('No project selected'));
    expect(api.skipSpecQueueItem).not.toHaveBeenCalled();
  });
});
