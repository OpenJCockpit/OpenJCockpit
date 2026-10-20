import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { WorkflowExecutionHistory } from './WorkflowExecutionHistory';
import type { WorkflowExecutionPage, WorkflowExecutionSummary } from '../workflowTypes';

function item(overrides: Partial<WorkflowExecutionSummary> = {}): WorkflowExecutionSummary {
  return {
    runId: 'run-1',
    workflowId: 'wf-1',
    status: 'COMPLETED',
    startedAt: '2024-01-01T00:00:00.000Z',
    completedAt: '2024-01-01T00:05:00.000Z',
    durationMillis: 300000,
    startedBy: 'alice',
    ...overrides,
  };
}

function page(overrides: Partial<WorkflowExecutionPage> = {}): WorkflowExecutionPage {
  return {
    items: [item()],
    limit: 20,
    offset: 0,
    total: 1,
    hasMore: false,
    ...overrides,
  };
}

const noop = () => {};

describe('WorkflowExecutionHistory', () => {
  it('renders rows in the given order without re-sorting', () => {
    const p = page({
      items: [
        item({ runId: 'run-c', startedAt: '2024-01-03T00:00:00.000Z' }),
        item({ runId: 'run-a', startedAt: '2024-01-01T00:00:00.000Z' }),
        item({ runId: 'run-b', startedAt: '2024-01-02T00:00:00.000Z' }),
      ],
      total: 3,
    });

    render(
      <WorkflowExecutionHistory
        page={p}
        loading={false}
        error={null}
        onSelect={noop}
        onRetry={noop}
        onLoadMore={noop}
      />,
    );

    const buttons = screen.getAllByRole('button', { name: /^run-/ });
    expect(buttons.map((b) => b.textContent)).toEqual(['run-c', 'run-a', 'run-b']);
  });

  it('shows the empty state and no error text when the page has no items', () => {
    render(
      <WorkflowExecutionHistory
        page={page({ items: [], total: 0 })}
        loading={false}
        error={null}
        onSelect={noop}
        onRetry={noop}
        onLoadMore={noop}
      />,
    );

    expect(screen.getByText('This workflow has not been executed yet.')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('shows an error message with a working Retry control', () => {
    const onRetry = vi.fn();
    render(
      <WorkflowExecutionHistory
        page={null}
        loading={false}
        error="Failed to load workflow executions: 500"
        onSelect={noop}
        onRetry={onRetry}
        onLoadMore={noop}
      />,
    );

    expect(screen.getByRole('alert')).toHaveTextContent('Failed to load workflow executions: 500');
    fireEvent.click(screen.getByRole('button', { name: 'Retry' }));
    expect(onRetry).toHaveBeenCalledTimes(1);
    expect(screen.queryByText('This workflow has not been executed yet.')).not.toBeInTheDocument();
  });

  it('shows a Load more button when hasMore is true and calls onLoadMore when clicked', () => {
    const onLoadMore = vi.fn();
    render(
      <WorkflowExecutionHistory
        page={page({ hasMore: true })}
        loading={false}
        error={null}
        onSelect={noop}
        onRetry={noop}
        onLoadMore={onLoadMore}
      />,
    );

    const loadMore = screen.getByText('Load more');
    fireEvent.click(loadMore);
    expect(onLoadMore).toHaveBeenCalledTimes(1);
  });

  it('does not show a Load more button when hasMore is false', () => {
    render(
      <WorkflowExecutionHistory
        page={page({ hasMore: false })}
        loading={false}
        error={null}
        onSelect={noop}
        onRetry={noop}
        onLoadMore={noop}
      />,
    );

    expect(screen.queryByText('Load more')).not.toBeInTheDocument();
  });

  it('calls onSelect with the exact runId when a row is activated by click, and the control is a real button', () => {
    const onSelect = vi.fn();
    render(
      <WorkflowExecutionHistory
        page={page({ items: [item({ runId: 'run-42' })] })}
        loading={false}
        error={null}
        onSelect={onSelect}
        onRetry={noop}
        onLoadMore={noop}
      />,
    );

    const runButton = screen.getByRole('button', { name: 'run-42' });
    expect(runButton.tagName).toBe('BUTTON');
    runButton.focus();
    fireEvent.click(runButton);
    expect(onSelect).toHaveBeenCalledWith('run-42');
  });

  it('renders the n/a placeholder for null completedAt, durationMillis, and startedBy', () => {
    render(
      <WorkflowExecutionHistory
        page={page({
          items: [
            item({
              completedAt: null,
              durationMillis: null,
              startedBy: null,
              status: 'RUNNING',
            }),
          ],
        })}
        loading={false}
        error={null}
        onSelect={noop}
        onRetry={noop}
        onLoadMore={noop}
      />,
    );

    const naCells = screen.getAllByText('n/a');
    expect(naCells.length).toBeGreaterThanOrEqual(3);
  });

  it('conveys status via an accessible text label, not color alone', () => {
    render(
      <WorkflowExecutionHistory
        page={page({ items: [item({ status: 'AWAITING_APPROVAL' })] })}
        loading={false}
        error={null}
        onSelect={noop}
        onRetry={noop}
        onLoadMore={noop}
      />,
    );

    expect(screen.getByText('Awaiting approval')).toBeInTheDocument();
  });
});
