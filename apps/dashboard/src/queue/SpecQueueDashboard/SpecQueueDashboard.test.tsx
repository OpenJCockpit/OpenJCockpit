import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../specQueueApi');
vi.mock('../../auth/roles', () => ({ isOpenJCockpitAdmin: () => false }));
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

import type { Project } from '../../types';
import * as api from '../specQueueApi';
import type { SpecQueue, SpecQueueItem } from '../specQueueTypes';
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

async function renderDashboard(props: { onOpenRun?: (w: string, r: string) => void } = {}) {
  render(<SpecQueueDashboard project={PROJECT} onBack={vi.fn()} {...props} />);
  await act(async () => {});
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
    render(<SpecQueueDashboard project={null} onBack={vi.fn()} />);
    expect(screen.getByText(/Selecteer eerst een project/)).toBeInTheDocument();
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
      const { unmount } = render(<SpecQueueDashboard project={PROJECT} onBack={vi.fn()} />);
      await act(async () => {});
      expect(api.getSpecQueue).toHaveBeenCalledTimes(1);

      vi.mocked(api.getSpecQueue).mockRejectedValue(new Error('offline'));
      await act(async () => {
        await vi.advanceTimersByTimeAsync(5000);
      });
      expect(api.getSpecQueue).toHaveBeenCalledTimes(2);
      expect(screen.getByTestId('queue-refresh-error')).toHaveTextContent('offline');
      expect(screen.getAllByTestId('queue-item')).toHaveLength(3);

      unmount();
      await act(async () => {
        await vi.advanceTimersByTimeAsync(15000);
      });
      expect(api.getSpecQueue).toHaveBeenCalledTimes(2);
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
    const row = screen.getAllByTestId('queue-item')[1];
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
});
