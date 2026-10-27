import { act, renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as api from '@openjcockpit/api';
import { useRunActions } from './useRunActions';

vi.mock('@openjcockpit/api', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@openjcockpit/api')>()),
  stopAgentRun: vi.fn(),
  submitApprovalDecision: vi.fn(),
}));

function setup(selectedRunId: string | null = 'run-1') {
  const client = new QueryClient({ defaultOptions: { mutations: { retry: false } } });
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  );
  const setDecisionError = vi.fn();
  const hook = renderHook(
    () =>
      useRunActions({
        selectedRunId,
        approvalContext: null,
        setRunStatus: vi.fn(),
        setApprovalContext: vi.fn(),
        setApprovalModalVisible: vi.fn(),
        setDecisionError,
      }),
    { wrapper },
  );
  return { ...hook, setDecisionError };
}

describe('useRunActions', () => {
  beforeEach(() => vi.clearAllMocks());

  it('falls back to a generic message when stopping fails without a message', async () => {
    vi.mocked(api.stopAgentRun).mockRejectedValue(new Error(''));
    const { result } = setup();

    act(() => result.current.handleStop('run-1'));

    await waitFor(() => expect(result.current.stopError).toBe('Failed to stop the run.'));
  });

  it('falls back to a generic message when stopping fails with a non-error', async () => {
    vi.mocked(api.stopAgentRun).mockRejectedValue('boom');
    const { result } = setup();

    act(() => result.current.handleStop('run-1'));

    await waitFor(() => expect(result.current.stopError).toBe('Failed to stop the run.'));
  });

  it('reports a decision submitted while no approval is pending', async () => {
    const { result, setDecisionError } = setup();

    act(() => result.current.submitDecision('ACCEPT'));

    await waitFor(() =>
      expect(setDecisionError).toHaveBeenLastCalledWith('No approval is pending.'),
    );
    expect(api.submitApprovalDecision).not.toHaveBeenCalled();
  });

  it('reports a decision submitted without a selected run', async () => {
    const { result, setDecisionError } = setup(null);

    act(() => result.current.submitDecision('ACCEPT'));

    await waitFor(() =>
      expect(setDecisionError).toHaveBeenLastCalledWith('No approval is pending.'),
    );
  });
});
