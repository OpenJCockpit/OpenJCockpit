import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { SpecQueueItemRow } from './SpecQueueItemRow';
import type { SpecQueueItem } from '../specQueueTypes';

function makeItem(extra: Partial<SpecQueueItem> = {}): SpecQueueItem {
  return {
    id: 'i1',
    projectId: 'p1',
    specFile: 'a.md',
    workflowId: 'w1',
    workflowName: 'Build it',
    autoMerge: false,
    status: 'QUEUED',
    pullRequestUrls: [],
    createdBy: 'me',
    createdAt: '2026-01-01T00:00:00Z',
    ...extra,
  };
}

function setup(item: SpecQueueItem, props: Partial<Parameters<typeof SpecQueueItemRow>[0]> = {}) {
  const handlers = {
    onMove: vi.fn(),
    onAutoMergeChange: vi.fn(),
    onRemove: vi.fn(),
    onCancelRun: vi.fn(),
    onRetry: vi.fn(),
    onSkip: vi.fn(),
    onOpenRun: vi.fn(),
  };
  render(
    <ul>
      <SpecQueueItemRow
        item={item}
        queuedIndex={item.status === 'QUEUED' ? 1 : undefined}
        queuedCount={3}
        autoMergeAllowed
        busy={false}
        {...handlers}
        {...props}
      />
    </ul>,
  );
  return handlers;
}

describe('SpecQueueItemRow', () => {
  it('shows spec, workflow name, status text with icon and position', () => {
    setup(makeItem());
    expect(screen.getByTestId('queue-item-spec')).toHaveTextContent('a.md');
    expect(screen.getByTestId('queue-item-workflow')).toHaveTextContent('Build it');
    expect(screen.getByTestId('queue-item-status')).toHaveTextContent('⏳ Queued');
    expect(screen.getByTestId('queue-item-position')).toHaveTextContent('2');
  });

  it('names move buttons after the spec and reports the direction', () => {
    const h = setup(makeItem());
    fireEvent.click(screen.getByRole('button', { name: 'Move a.md up' }));
    fireEvent.click(screen.getByRole('button', { name: 'Move a.md down' }));
    expect(h.onMove).toHaveBeenNthCalledWith(1, expect.anything(), 'up');
    expect(h.onMove).toHaveBeenNthCalledWith(2, expect.anything(), 'down');
  });

  it('disables Up at the top and Down at the bottom', () => {
    setup(makeItem(), { queuedIndex: 0 });
    expect(screen.getByTestId('queue-item-move-up')).toBeDisabled();
    expect(screen.getByTestId('queue-item-move-down')).not.toBeDisabled();
  });

  it('disables Down at the bottom', () => {
    setup(makeItem(), { queuedIndex: 2 });
    expect(screen.getByTestId('queue-item-move-down')).toBeDisabled();
  });

  it('describes the auto-merge checkbox and reports changes', () => {
    const h = setup(makeItem());
    const box = screen.getByRole('checkbox', { name: 'Auto-merge a.md' });
    expect(box).toHaveAccessibleDescription(/Merged without human review/);
    fireEvent.click(box);
    expect(h.onAutoMergeChange).toHaveBeenCalledWith(expect.anything(), true);
  });

  it('disables auto-merge with the reason when not allowed', () => {
    setup(makeItem(), { autoMergeAllowed: false });
    const box = screen.getByTestId('queue-item-auto-merge');
    expect(box).toBeDisabled();
    expect(box).toHaveAccessibleDescription(/not allowed for this project/);
  });

  it('shows a read-only indicator for non-editable items', () => {
    setup(makeItem({ status: 'RUNNING', autoMerge: true }));
    expect(screen.getByTestId('queue-item-auto-merge-indicator')).toHaveTextContent(
      'Auto-merge: on',
    );
    expect(screen.queryByTestId('queue-item-auto-merge')).not.toBeInTheDocument();
  });

  it('offers Remove for queued items and no Cancel', () => {
    const h = setup(makeItem());
    fireEvent.click(screen.getByRole('button', { name: 'Remove a.md from the queue' }));
    expect(h.onRemove).toHaveBeenCalled();
    expect(screen.queryByTestId('queue-item-cancel')).not.toBeInTheDocument();
  });

  it('offers Cancel and the run link for a running item, with "Awaiting approval" at a gate', () => {
    const h = setup(
      makeItem({ status: 'RUNNING', workflowRunId: 'r1', runStatus: 'AWAITING_APPROVAL' }),
    );
    expect(screen.getByTestId('queue-item-status')).toHaveTextContent('Awaiting approval');
    fireEvent.click(screen.getByRole('button', { name: 'Cancel a.md' }));
    fireEvent.click(screen.getByRole('button', { name: 'View the run of a.md' }));
    expect(h.onCancelRun).toHaveBeenCalled();
    expect(h.onOpenRun).toHaveBeenCalled();
    expect(screen.queryByTestId('queue-item-remove')).not.toBeInTheDocument();
  });

  it('shows the failure reason with Retry and Skip for a failed item', () => {
    const h = setup(makeItem({ status: 'FAILED', failureReason: 'RUN_FAILED' }));
    expect(screen.getByTestId('queue-item-failure')).toHaveTextContent('The run failed');
    fireEvent.click(screen.getByRole('button', { name: 'Retry a.md' }));
    fireEvent.click(screen.getByRole('button', { name: 'Skip a.md' }));
    expect(h.onRetry).toHaveBeenCalled();
    expect(h.onSkip).toHaveBeenCalled();
  });

  it('renders safe PR links with rel and drops unsafe ones', () => {
    setup(
      makeItem({
        status: 'AWAITING_MERGE',
        pullRequestUrls: ['https://github.com/o/r/pull/1', 'javascript:alert(1)'],
      }),
    );
    const links = screen.getAllByTestId('queue-item-pr-link');
    expect(links).toHaveLength(1);
    expect(links[0]).toHaveAttribute('rel', 'noopener noreferrer');
    expect(links[0]).toHaveAttribute('target', '_blank');
    expect(links[0]).toHaveTextContent('Pull request ↗');
  });

  it('numbers several PR links', () => {
    setup(
      makeItem({
        status: 'MERGED',
        pullRequestUrls: ['https://github.com/o/r/pull/1', 'https://github.com/o/r/pull/2'],
      }),
    );
    expect(screen.getAllByTestId('queue-item-pr-link').map((a) => a.textContent)).toEqual([
      'Pull request 1 ↗',
      'Pull request 2 ↗',
    ]);
  });

  it('shows merge progress for a merging item', () => {
    setup(makeItem({ status: 'MERGING', mergeState: 'WAITING_FOR_MERGEABILITY' }));
    expect(screen.getByTestId('queue-item-merge-state')).toHaveTextContent(
      'Waiting until the pull request can be merged',
    );
  });

  it('disables every control while busy', () => {
    setup(makeItem(), { busy: true });
    const controls = [...screen.getAllByRole('button'), ...screen.getAllByRole('checkbox')];
    expect(controls.length).toBeGreaterThan(0);
    controls.forEach((c) => expect(c).toBeDisabled());
  });
});
