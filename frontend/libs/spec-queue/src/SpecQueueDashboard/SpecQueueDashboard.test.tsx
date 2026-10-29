import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('@openjcockpit/api', async (importOriginal) =>
  (await import('../testUtils')).automockApiClients(await importOriginal()),
);
vi.mock('@openjcockpit/auth', () => ({ isOpenJCockpitAdmin: vi.fn(() => false) }));
vi.mock('../AddToQueueDialog/AddToQueueDialog', () => ({
  AddToQueueDialog: ({
    onAdded,
    onCancel,
  }: {
    onAdded: (i: { specFile: string }) => void;
    onCancel: () => void;
  }) => (
    <div role="dialog" aria-label="Add to queue">
      <button onClick={() => onAdded({ specFile: 'new.md' })}>stub-add</button>
      <button onClick={onCancel}>stub-cancel</button>
    </div>
  ),
}));

import type { Project, SpecQueue, SpecQueueItem } from '@openjcockpit/api';
import * as api from '@openjcockpit/api';
import { isOpenJCockpitAdmin } from '@openjcockpit/auth';
import { withI18n } from '@openjcockpit/i18n/testing';
import { specQueueResources } from '../i18n';
import { withQueryClient } from '../testQueryClient';
import { SpecQueueDashboard } from './SpecQueueDashboard';

const PROJECT = { id: 'p1', name: 'Proj' } as Project;

function item(id: string, extra: Partial<SpecQueueItem> = {}): SpecQueueItem {
  return {
    id,
    projectId: 'p1',
    specFile: `${id}.md`,
    workflowId: 'w1',
    workflowName: 'Build',
    autoMerge: false,
    status: 'QUEUED',
    pullRequestUrls: [],
    createdBy: 'me',
    createdAt: '2026-01-01T00:00:00Z',
    ...extra,
  };
}

function queue(extra: Partial<SpecQueue> = {}): SpecQueue {
  return {
    projectId: 'p1',
    state: 'ACTIVE',
    autoMergeAllowed: false,
    items: [item('a'), item('b'), item('c')],
    recentlyFinished: [],
    runner: { enabled: true, configured: true },
    ...extra,
  };
}

/** Lets resolved mocks reach the DOM; react-query notifies observers on a zero-delay timer. */
async function settle() {
  await act(async () => {
    if (vi.isFakeTimers()) await vi.advanceTimersByTimeAsync(1);
    else await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

async function renderDashboard(props: { onOpenRun?: (w: string, r: string) => void } = {}) {
  render(withQueryClient(<SpecQueueDashboard project={PROJECT} onBack={vi.fn()} {...props} />));
  await waitFor(() => expect(screen.queryByTestId('queue-loading')).not.toBeInTheDocument());
}

describe('SpecQueueDashboard', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(api.getSpecQueue).mockResolvedValue(queue());
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('asks for a project when none is selected', () => {
    render(withQueryClient(<SpecQueueDashboard project={null} onBack={vi.fn()} />));
    expect(screen.getByText(/Select a project first/)).toBeInTheDocument();
    expect(api.getSpecQueue).not.toHaveBeenCalled();
  });

  it('lists items in order with status, workflow and state', async () => {
    await renderDashboard();
    const rows = screen.getAllByTestId('queue-item');
    expect(rows.map((r) => r.getAttribute('data-item-id'))).toEqual(['a', 'b', 'c']);
    expect(screen.getAllByTestId('queue-item-status')[0]).toHaveTextContent('Queued');
    expect(screen.getAllByTestId('queue-item-workflow')[0]).toHaveTextContent('Build');
    expect(screen.getByTestId('queue-state')).toHaveTextContent('State: Active');
  });

  it('shows the empty state', async () => {
    vi.mocked(api.getSpecQueue).mockResolvedValue(queue({ items: [] }));
    await renderDashboard();
    expect(screen.getByTestId('queue-empty')).toBeInTheDocument();
  });

  it('shows the load error on a failed first load', async () => {
    vi.mocked(api.getSpecQueue).mockRejectedValue(new Error('boom'));
    await renderDashboard();
    expect(screen.getByTestId('queue-load-error')).toHaveTextContent(
      'The queue could not be loaded — boom',
    );
  });

  describe('polling', () => {
    beforeEach(() => vi.useFakeTimers());

    it('polls every 5 s, keeps stale data on failure, and stops on unmount', async () => {
      const { unmount } = render(
        withQueryClient(<SpecQueueDashboard project={PROJECT} onBack={vi.fn()} />),
      );
      await settle();
      expect(api.getSpecQueue).toHaveBeenCalledTimes(1);

      vi.mocked(api.getSpecQueue).mockRejectedValue(new Error('offline'));
      await act(async () => {
        await vi.advanceTimersByTimeAsync(5000);
      });
      await settle();
      expect(api.getSpecQueue).toHaveBeenCalledTimes(2);
      expect(screen.getByTestId('queue-refresh-error')).toHaveTextContent('offline');
      expect(screen.getAllByTestId('queue-item')).toHaveLength(3);

      unmount();
      await act(async () => {
        await vi.advanceTimersByTimeAsync(15000);
      });
      expect(api.getSpecQueue).toHaveBeenCalledTimes(2);
    });

    it('skips a poll tick while the previous request is still in flight', async () => {
      render(withQueryClient(<SpecQueueDashboard project={PROJECT} onBack={vi.fn()} />));
      await settle();
      vi.mocked(api.getSpecQueue).mockReturnValue(new Promise(() => {}));
      await act(async () => {
        await vi.advanceTimersByTimeAsync(5000);
      });
      await act(async () => {
        await vi.advanceTimersByTimeAsync(15000);
      });
      expect(api.getSpecQueue).toHaveBeenCalledTimes(2);
    });

    it('backs off exponentially while loads keep failing', async () => {
      vi.mocked(api.getSpecQueue).mockRejectedValue(new Error('offline'));
      render(withQueryClient(<SpecQueueDashboard project={PROJECT} onBack={vi.fn()} />));
      await act(async () => {}); // no clock advance: the retry delay counts from the failure
      expect(api.getSpecQueue).toHaveBeenCalledTimes(1);
      await act(async () => {
        await vi.advanceTimersByTimeAsync(9999); // first retry waits 10 s
      });
      expect(api.getSpecQueue).toHaveBeenCalledTimes(1);
      await act(async () => {
        await vi.advanceTimersByTimeAsync(1);
      });
      expect(api.getSpecQueue).toHaveBeenCalledTimes(2);
      await act(async () => {
        await vi.advanceTimersByTimeAsync(19999); // then 20 s
      });
      expect(api.getSpecQueue).toHaveBeenCalledTimes(2);
      await act(async () => {
        await vi.advanceTimersByTimeAsync(1);
      });
      expect(api.getSpecQueue).toHaveBeenCalledTimes(3);
    });

    it('stops polling while the tab is hidden and reloads when it is visible again', async () => {
      let hidden = false;
      const spy = vi.spyOn(document, 'hidden', 'get').mockImplementation(() => hidden);
      // react-query's focus manager reads visibilityState, so keep both in step.
      const stateSpy = vi
        .spyOn(document, 'visibilityState', 'get')
        .mockImplementation(() => (hidden ? 'hidden' : 'visible'));
      try {
        render(withQueryClient(<SpecQueueDashboard project={PROJECT} onBack={vi.fn()} />));
        await settle();
        expect(api.getSpecQueue).toHaveBeenCalledTimes(1);

        hidden = true;
        await act(async () => {
          document.dispatchEvent(new Event('visibilitychange', { bubbles: true }));
          await vi.advanceTimersByTimeAsync(30000);
        });
        expect(api.getSpecQueue).toHaveBeenCalledTimes(1);

        hidden = false;
        await act(async () => {
          document.dispatchEvent(new Event('visibilitychange', { bubbles: true }));
        });
        expect(api.getSpecQueue).toHaveBeenCalledTimes(2);
      } finally {
        spy.mockRestore();
        stateSpy.mockRestore();
      }
    });

    it('ignores a poll response that resolves after a newer reorder response', async () => {
      render(withQueryClient(<SpecQueueDashboard project={PROJECT} onBack={vi.fn()} />));
      await settle();

      let resolvePoll!: (q: SpecQueue) => void;
      vi.mocked(api.getSpecQueue).mockReturnValue(
        new Promise<SpecQueue>((resolve) => {
          resolvePoll = resolve;
        }),
      );
      await act(async () => {
        await vi.advanceTimersByTimeAsync(5000);
      });

      vi.mocked(api.reorderSpecQueue).mockResolvedValue(
        queue({ items: [item('b'), item('a'), item('c')] }),
      );
      fireEvent.click(screen.getByRole('button', { name: 'Move a.md down' }));
      await settle();
      expect(screen.getAllByTestId('queue-item')[0]).toHaveAttribute('data-item-id', 'b');

      await act(async () => resolvePoll(queue())); // stale order a, b, c
      expect(screen.getAllByTestId('queue-item')[0]).toHaveAttribute('data-item-id', 'b');
    });

    it('drops a response for the previous project after switching projects', async () => {
      let resolveOld!: (q: SpecQueue) => void;
      vi.mocked(api.getSpecQueue).mockReturnValueOnce(
        new Promise<SpecQueue>((resolve) => {
          resolveOld = resolve;
        }),
      );
      const other = { id: 'p2', name: 'Other' } as Project;
      // One client across both renders, so the project switch is a key change, not a new cache.
      const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } });
      const dashboard = (project: Project) =>
        withI18n(
          <QueryClientProvider client={client}>
            <SpecQueueDashboard project={project} onBack={vi.fn()} />
          </QueryClientProvider>,
          specQueueResources,
        );
      const { rerender } = render(dashboard(PROJECT));
      vi.mocked(api.getSpecQueue).mockResolvedValue(queue({ projectId: 'p2', items: [item('z')] }));
      rerender(dashboard(other));
      await settle();

      await act(async () => resolveOld(queue()));
      expect(
        screen.getAllByTestId('queue-item').map((r) => r.getAttribute('data-item-id')),
      ).toEqual(['z']);
    });
  });

  describe('reorder', () => {
    it('sends the full swapped id list, keeps focus on the same button and announces', async () => {
      const reordered = queue({ items: [item('b'), item('a'), item('c')] });
      vi.mocked(api.reorderSpecQueue).mockResolvedValue(reordered);
      await renderDashboard();
      fireEvent.click(screen.getByRole('button', { name: 'Move a.md down' }));
      await waitFor(() =>
        expect(screen.getAllByTestId('queue-item')[0]).toHaveAttribute('data-item-id', 'b'),
      );
      expect(api.reorderSpecQueue).toHaveBeenCalledWith('p1', ['b', 'a', 'c']);
      expect(screen.getByRole('button', { name: 'Move a.md down' })).toHaveFocus();
      expect(screen.getByTestId('queue-announcer')).toHaveTextContent(
        'a.md moved to position 2 of 3',
      );
    });

    it('moves focus to the opposite button when the item hits a boundary', async () => {
      vi.mocked(api.reorderSpecQueue).mockResolvedValue(
        queue({ items: [item('b'), item('a'), item('c')] }),
      );
      await renderDashboard();
      fireEvent.click(screen.getByRole('button', { name: 'Move b.md up' }));
      await waitFor(() =>
        expect(screen.getAllByTestId('queue-item')[0]).toHaveAttribute('data-item-id', 'b'),
      );
      // b is now first, so Up is disabled and focus falls back to Down.
      expect(screen.getByRole('button', { name: 'Move b.md up' })).toBeDisabled();
      expect(screen.getByRole('button', { name: 'Move b.md down' })).toHaveFocus();
    });

    it('shows the server message on a stale 409 and refreshes', async () => {
      vi.mocked(api.reorderSpecQueue).mockRejectedValue(new Error('The order is stale'));
      await renderDashboard();
      fireEvent.click(screen.getByRole('button', { name: 'Move a.md down' }));
      expect(await screen.findByTestId('queue-action-error')).toHaveTextContent(
        'The order is stale',
      );
      expect(api.getSpecQueue).toHaveBeenCalledTimes(2);
    });
  });

  it('uses native buttons in reading order', async () => {
    await renderDashboard();
    const row = screen.getAllByTestId('queue-item')[1]!;
    const buttons = Array.from(row.querySelectorAll('button')).map((b) => b.tagName);
    expect(buttons.every((t) => t === 'BUTTON')).toBe(true);
    expect(Array.from(row.querySelectorAll('button')).map((b) => b.dataset.testid)).toEqual([
      'queue-item-move-up',
      'queue-item-move-down',
      'queue-item-remove',
    ]);
  });

  describe('confirmations', () => {
    it('removes only after confirmation and returns focus to the trigger', async () => {
      vi.mocked(api.removeSpecQueueItem).mockResolvedValue(item('a', { status: 'REMOVED' }));
      await renderDashboard();
      const trigger = screen.getByRole('button', { name: 'Remove a.md from the queue' });
      trigger.focus();
      fireEvent.click(trigger);
      expect(api.removeSpecQueueItem).not.toHaveBeenCalled();
      expect(screen.getByRole('dialog', { name: 'Remove a.md?' })).toBeInTheDocument();
      fireEvent.click(screen.getByTestId('confirm-action-confirm'));
      await waitFor(() => expect(api.removeSpecQueueItem).toHaveBeenCalledWith('p1', 'a'));
      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
      expect(screen.getByTestId('queue-announcer')).toHaveTextContent('a.md removed');
    });

    it('keeps the dialog open and shows the server message when a cancel fails', async () => {
      vi.mocked(api.getSpecQueue).mockResolvedValue(
        queue({ items: [item('a', { status: 'RUNNING', workflowRunId: 'r1' })] }),
      );
      vi.mocked(api.removeSpecQueueItem).mockRejectedValue(new Error('Item changed state'));
      await renderDashboard();
      fireEvent.click(screen.getByRole('button', { name: 'Cancel a.md' }));
      fireEvent.click(screen.getByTestId('confirm-action-confirm'));
      expect(await screen.findByTestId('confirm-action-error')).toHaveTextContent(
        'Item changed state',
      );
      expect(screen.getByRole('dialog', { name: 'Cancel a.md?' })).toBeInTheDocument();
    });

    it('skip needs confirmation; retry does not', async () => {
      vi.mocked(api.getSpecQueue).mockResolvedValue(
        queue({
          state: 'HALTED',
          items: [item('a', { status: 'FAILED', failureReason: 'RUN_FAILED' })],
        }),
      );
      vi.mocked(api.skipSpecQueueItem).mockResolvedValue(item('a', { status: 'SKIPPED' }));
      vi.mocked(api.retrySpecQueueItem).mockResolvedValue(item('a'));
      await renderDashboard();
      fireEvent.click(screen.getByRole('button', { name: 'Retry a.md' }));
      await waitFor(() => expect(api.retrySpecQueueItem).toHaveBeenCalledWith('p1', 'a'));
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();

      fireEvent.click(screen.getByRole('button', { name: 'Skip a.md' }));
      expect(api.skipSpecQueueItem).not.toHaveBeenCalled();
      fireEvent.click(screen.getByTestId('confirm-action-confirm'));
      await waitFor(() => expect(api.skipSpecQueueItem).toHaveBeenCalledWith('p1', 'a'));
    });
  });

  describe('pause and resume', () => {
    it('pauses and shows the banner, then resumes', async () => {
      vi.mocked(api.pauseSpecQueue).mockResolvedValue(queue({ state: 'PAUSED' }));
      vi.mocked(api.resumeSpecQueue).mockResolvedValue(queue());
      await renderDashboard();
      expect(screen.queryByTestId('queue-resume')).not.toBeInTheDocument();
      fireEvent.click(screen.getByTestId('queue-pause'));
      expect(await screen.findByTestId('queue-banner-paused')).toHaveAttribute('role', 'status');
      fireEvent.click(screen.getByTestId('queue-resume'));
      await waitFor(() =>
        expect(screen.queryByTestId('queue-banner-paused')).not.toBeInTheDocument(),
      );
      expect(screen.getByTestId('queue-announcer')).toHaveTextContent('Queue resumed');
    });

    it('shows Resume and a warning banner when halted', async () => {
      vi.mocked(api.getSpecQueue).mockResolvedValue(queue({ state: 'HALTED' }));
      await renderDashboard();
      expect(screen.getByTestId('queue-banner-halted')).toBeInTheDocument();
      expect(screen.getByTestId('queue-resume')).toBeInTheDocument();
      expect(screen.queryByTestId('queue-pause')).not.toBeInTheDocument();
    });

    it('shows the server message when resume is refused', async () => {
      vi.mocked(api.getSpecQueue).mockResolvedValue(queue({ state: 'HALTED' }));
      vi.mocked(api.resumeSpecQueue).mockRejectedValue(new Error('A failed item blocks the queue'));
      await renderDashboard();
      fireEvent.click(screen.getByTestId('queue-resume'));
      expect(await screen.findByTestId('queue-action-error')).toHaveTextContent(
        'A failed item blocks the queue',
      );
    });
  });

  it('shows runner and poll-error banners as status', async () => {
    vi.mocked(api.getSpecQueue).mockResolvedValue(
      queue({
        runner: { enabled: false, configured: false },
        lastPollError: { code: 'GITHUB_UNAVAILABLE', at: '2026-01-01T00:00:00Z' },
      }),
    );
    await renderDashboard();
    expect(screen.getByTestId('queue-banner-runner-disabled')).toHaveAttribute('role', 'status');
    expect(screen.queryByTestId('queue-banner-runner-not-configured')).not.toBeInTheDocument();
    expect(screen.getByTestId('queue-banner-poll-error')).toHaveTextContent(
      'GitHub is unavailable',
    );
  });

  it('shows the not-configured banner when enabled without identity', async () => {
    vi.mocked(api.getSpecQueue).mockResolvedValue(
      queue({ runner: { enabled: true, configured: false } }),
    );
    await renderDashboard();
    expect(screen.getByTestId('queue-banner-runner-not-configured')).toBeInTheDocument();
  });

  it('updates the auto-merge flag when allowed', async () => {
    vi.mocked(api.getSpecQueue).mockResolvedValue(queue({ autoMergeAllowed: true }));
    vi.mocked(api.updateSpecQueueItem).mockResolvedValue(item('a', { autoMerge: true }));
    await renderDashboard();
    fireEvent.click(screen.getByRole('checkbox', { name: 'Auto-merge a.md' }));
    await waitFor(() =>
      expect(api.updateSpecQueueItem).toHaveBeenCalledWith('p1', 'a', { autoMerge: true }),
    );
  });

  it('shows the read-only project toggle with an explanation for non-admins', async () => {
    await renderDashboard();
    expect(screen.getByTestId('auto-merge-setting-toggle')).toBeDisabled();
    expect(screen.getByTestId('auto-merge-setting-description')).toHaveTextContent(
      'Only administrators',
    );
  });

  it('opens the add dialog, adds, announces and refreshes', async () => {
    await renderDashboard();
    fireEvent.click(screen.getByTestId('queue-add'));
    fireEvent.click(screen.getByText('stub-add'));
    await waitFor(() => expect(api.getSpecQueue).toHaveBeenCalledTimes(2));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.getByTestId('queue-announcer')).toHaveTextContent('new.md added to the queue');
  });

  it('opens a run through onOpenRun', async () => {
    vi.mocked(api.getSpecQueue).mockResolvedValue(
      queue({ items: [item('a', { status: 'RUNNING', workflowRunId: 'run-42' })] }),
    );
    const onOpenRun = vi.fn();
    await renderDashboard({ onOpenRun });
    fireEvent.click(screen.getByTestId('queue-item-run-link'));
    expect(onOpenRun).toHaveBeenCalledWith('w1', 'run-42');
  });

  it('lists recently finished items with a PR link', async () => {
    vi.mocked(api.getSpecQueue).mockResolvedValue(
      queue({
        recentlyFinished: [
          item('z', { status: 'MERGED', pullRequestUrls: ['https://github.com/o/r/pull/9'] }),
        ],
      }),
    );
    await renderDashboard();
    const recent = screen.getByTestId('queue-recent');
    expect(recent).toHaveTextContent('z.md');
    expect(recent.querySelector('[data-testid="queue-item-pr-link"]')).toBeInTheDocument();
  });

  it('falls back to a generic load error for a non-Error rejection', async () => {
    vi.mocked(api.getSpecQueue).mockRejectedValue('boom');
    await renderDashboard();
    expect(screen.getByTestId('queue-load-error')).toHaveTextContent(
      'The queue could not be loaded — Failed to load the spec queue',
    );
  });

  it('does not poll when the tab is hidden from the start', async () => {
    vi.useFakeTimers();
    const spy = vi.spyOn(document, 'hidden', 'get').mockReturnValue(true);
    try {
      render(withQueryClient(<SpecQueueDashboard project={PROJECT} onBack={vi.fn()} />));
      await settle();
      await act(async () => {
        await vi.advanceTimersByTimeAsync(30000);
      });
      expect(api.getSpecQueue).toHaveBeenCalledTimes(1);
    } finally {
      spy.mockRestore();
    }
  });

  it('shows when the runner last checked', async () => {
    vi.mocked(api.getSpecQueue).mockResolvedValue(queue({ lastPolledAt: '2026-01-01T00:00:00Z' }));
    await renderDashboard();
    expect(screen.getByTestId('queue-last-polled')).toHaveTextContent('Runner last checked:');
  });

  it('closes the remove confirmation on cancel without calling the API', async () => {
    await renderDashboard();
    fireEvent.click(screen.getByRole('button', { name: 'Remove a.md from the queue' }));
    fireEvent.click(screen.getByTestId('confirm-action-cancel'));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(api.removeSpecQueueItem).not.toHaveBeenCalled();
  });

  it('closes the add dialog on cancel', async () => {
    await renderDashboard();
    fireEvent.click(screen.getByTestId('queue-add'));
    fireEvent.click(screen.getByText('stub-cancel'));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('keeps focus handling quiet when the moved row is gone from the response', async () => {
    vi.mocked(api.reorderSpecQueue).mockResolvedValue(queue({ items: [item('b'), item('c')] }));
    await renderDashboard();
    fireEvent.click(screen.getByRole('button', { name: 'Move a.md down' }));
    await waitFor(() => expect(screen.getAllByTestId('queue-item')).toHaveLength(2));
    expect(api.reorderSpecQueue).toHaveBeenCalledWith('p1', ['b', 'a', 'c']);
  });

  describe('unmounting during an action', () => {
    it('does not apply a reorder response after unmount', async () => {
      let resolve!: (q: SpecQueue) => void;
      vi.mocked(api.reorderSpecQueue).mockReturnValue(new Promise((r) => (resolve = r)));
      render(withQueryClient(<SpecQueueDashboard project={PROJECT} onBack={vi.fn()} />));
      fireEvent.click(await screen.findByRole('button', { name: 'Move a.md down' }));
      cleanup();
      await act(async () => resolve(queue()));
      expect(api.getSpecQueue).toHaveBeenCalledTimes(1);
    });

    it('does not apply a pause response after unmount', async () => {
      let resolve!: (q: SpecQueue) => void;
      vi.mocked(api.pauseSpecQueue).mockReturnValue(new Promise((r) => (resolve = r)));
      render(withQueryClient(<SpecQueueDashboard project={PROJECT} onBack={vi.fn()} />));
      fireEvent.click(await screen.findByTestId('queue-pause'));
      cleanup();
      await act(async () => resolve(queue({ state: 'PAUSED' })));
      expect(api.pauseSpecQueue).toHaveBeenCalledTimes(1);
    });

    it('does not touch state when a failing action settles after unmount', async () => {
      let reject!: (e: Error) => void;
      vi.mocked(api.pauseSpecQueue).mockReturnValue(new Promise((_, r) => (reject = r)));
      render(withQueryClient(<SpecQueueDashboard project={PROJECT} onBack={vi.fn()} />));
      fireEvent.click(await screen.findByTestId('queue-pause'));
      cleanup();
      await act(async () => reject(new Error('late')));
      expect(api.pauseSpecQueue).toHaveBeenCalledTimes(1);
    });

    it('does not apply a confirmed removal after unmount', async () => {
      let resolve!: (i: SpecQueueItem) => void;
      vi.mocked(api.removeSpecQueueItem).mockReturnValue(new Promise((r) => (resolve = r)));
      render(withQueryClient(<SpecQueueDashboard project={PROJECT} onBack={vi.fn()} />));
      fireEvent.click(await screen.findByRole('button', { name: 'Remove a.md from the queue' }));
      fireEvent.click(screen.getByTestId('confirm-action-confirm'));
      cleanup();
      await act(async () => resolve(item('a')));
      expect(api.getSpecQueue).toHaveBeenCalledTimes(1);
    });

    it('does not show a removal failure after unmount', async () => {
      let reject!: (e: Error) => void;
      vi.mocked(api.removeSpecQueueItem).mockReturnValue(new Promise((_, r) => (reject = r)));
      render(withQueryClient(<SpecQueueDashboard project={PROJECT} onBack={vi.fn()} />));
      fireEvent.click(await screen.findByRole('button', { name: 'Remove a.md from the queue' }));
      fireEvent.click(screen.getByTestId('confirm-action-confirm'));
      cleanup();
      await act(async () => reject(new Error('late')));
      expect(api.removeSpecQueueItem).toHaveBeenCalledTimes(1);
    });
  });

  describe('project auto-merge setting', () => {
    const enable = async () => {
      vi.mocked(isOpenJCockpitAdmin).mockReturnValue(true);
      vi.mocked(api.updateSpecQueueSettings).mockResolvedValue({ autoMergeAllowed: true });
      await renderDashboard();
      fireEvent.click(screen.getByTestId('auto-merge-setting-toggle'));
      fireEvent.click(screen.getByTestId('confirm-action-confirm'));
    };

    it('applies the saved setting to the cached queue', async () => {
      await enable();
      await waitFor(() => expect(screen.getByTestId('auto-merge-setting-toggle')).toBeChecked());
    });

    it('leaves an evicted queue cache untouched', async () => {
      const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
      vi.mocked(isOpenJCockpitAdmin).mockReturnValue(true);
      vi.mocked(api.updateSpecQueueSettings).mockImplementation(async () => {
        client.removeQueries({ queryKey: ['spec-queue', 'p1', 'queue'] });
        return { autoMergeAllowed: true };
      });
      render(
        withI18n(
          <QueryClientProvider client={client}>
            <SpecQueueDashboard project={PROJECT} onBack={vi.fn()} />
          </QueryClientProvider>,
          specQueueResources,
        ),
      );
      fireEvent.click(await screen.findByTestId('auto-merge-setting-toggle'));
      fireEvent.click(screen.getByTestId('confirm-action-confirm'));
      await waitFor(() => expect(api.updateSpecQueueSettings).toHaveBeenCalled());
      await settle();
      expect(client.getQueryData(['spec-queue', 'p1', 'queue'])).toBeUndefined();
    });
  });
});
