import { act, renderHook } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as api from '@openjcockpit/api';
import type { Project, SpecFile, WorkflowDefinition } from '@openjcockpit/api';
import { useRunLauncher } from './useRunLauncher';

vi.mock('@openjcockpit/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@openjcockpit/api')>();
  return {
    ...actual,
    checkWorkflowPreflight: vi.fn(),
    initSpecFolder: vi.fn(),
    startWorkflow: vi.fn(),
  };
});

const project = { id: 'p1', name: 'P', gitUrl: 'https://git/x' } as Project;
const plain = { id: 'w1', name: 'Plain' } as WorkflowDefinition;
const prompted = { id: 'w2', name: 'Prompted', promptRequired: true } as WorkflowDefinition;
const specCreate = { id: 'wf-spec-create', name: 'Create spec' } as WorkflowDefinition;
const spec = { fileName: 'a.md' } as SpecFile;

function setup(options: Partial<Parameters<typeof useRunLauncher>[0]> = {}) {
  return renderHook(() =>
    useRunLauncher({
      selectedProject: project,
      projectWorkflows: [plain, prompted],
      selectedWorkflowId: 'w1',
      selectedSpec: spec,
      ...options,
    }),
  );
}

describe('useRunLauncher', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(api.checkWorkflowPreflight).mockResolvedValue({ passed: true, errors: [] });
    vi.mocked(api.startWorkflow).mockResolvedValue({ executionId: 'run-1' } as never);
  });

  it('starts idle with no run', () => {
    const { result } = setup();
    expect(result.current.buttonState).toBe('idle');
    expect(result.current.activeRun).toBeNull();
  });

  it('runs pre-flight, starts the workflow with the project context and tracks the run', async () => {
    const { result } = setup();
    await act(() => result.current.handleStartWorkflow());
    expect(api.checkWorkflowPreflight).toHaveBeenCalledWith('p1');
    expect(api.startWorkflow).toHaveBeenCalledWith('w1', {
      specFile: 'a.md',
      repositoryUrl: 'https://git/x',
      projectId: 'p1',
    });
    expect(result.current.activeRun).toEqual({ workflow: plain, runId: 'run-1' });
    expect(result.current.buttonState).toBe('idle');
    expect(result.current.runEnded).toBe(false);
  });

  it('does nothing when the selected workflow is unknown', async () => {
    const { result } = setup({ selectedWorkflowId: 'missing' });
    await act(() => result.current.handleStartWorkflow());
    expect(api.checkWorkflowPreflight).not.toHaveBeenCalled();
  });

  it('asks for a prompt first when the workflow requires one', async () => {
    const { result } = setup({ selectedWorkflowId: 'w2' });
    await act(() => result.current.handleStartWorkflow());
    expect(result.current.promptWorkflow).toBe(prompted);
    expect(api.startWorkflow).not.toHaveBeenCalled();

    await act(() => result.current.handlePromptSubmit('do the thing'));
    expect(api.startWorkflow).toHaveBeenCalledWith(
      'w2',
      expect.objectContaining({ prompt: 'do the thing' }),
    );
    expect(result.current.promptWorkflow).toBeNull();
    expect(result.current.activeRun?.runId).toBe('run-1');
  });

  it('ignores a prompt submission without a pending prompt', async () => {
    const { result } = setup();
    await act(() => result.current.handlePromptSubmit('x'));
    expect(api.startWorkflow).not.toHaveBeenCalled();
  });

  it('cancels the prompt', async () => {
    const { result } = setup({ selectedWorkflowId: 'w2' });
    await act(() => result.current.handleStartWorkflow());
    act(() => result.current.cancelPrompt());
    expect(result.current.promptWorkflow).toBeNull();
    expect(result.current.buttonState).toBe('idle');
  });

  it('surfaces failed pre-flight checks and does not start', async () => {
    vi.mocked(api.checkWorkflowPreflight).mockResolvedValue({
      passed: false,
      errors: [{ code: 'DOCKER_UNAVAILABLE', message: 'no docker' }],
    });
    const { result } = setup({ selectedWorkflowId: 'w2' });
    await act(() => result.current.handleStartWorkflow());
    await act(() => result.current.handlePromptSubmit('x'));
    expect(result.current.preflightErrors).toEqual([
      { code: 'DOCKER_UNAVAILABLE', message: 'no docker' },
    ]);
    expect(result.current.promptWorkflow).toBeNull();
    expect(result.current.buttonState).toBe('idle');
    expect(api.startWorkflow).not.toHaveBeenCalled();

    act(() => result.current.dismissPreflightErrors());
    expect(result.current.preflightErrors).toBeNull();
  });

  it('reports a pre-flight call that itself fails', async () => {
    vi.mocked(api.checkWorkflowPreflight).mockRejectedValue(new Error('x'));
    const { result } = setup();
    await act(() => result.current.handleStartWorkflow());
    expect(result.current.preflightErrors).toEqual([
      { code: 'UNKNOWN_ERROR', message: 'Pre-flight check could not be completed.' },
    ]);
  });

  it('puts the button in the error state with the failure message when starting fails', async () => {
    vi.mocked(api.startWorkflow).mockRejectedValue(new Error('queue item active'));
    const { result } = setup();
    await act(() => result.current.handleStartWorkflow());
    expect(result.current.startError).toBe('queue item active');
    expect(result.current.buttonState).toBe('error');
    expect(result.current.activeRun).toBeNull();
  });

  it('falls back to a generic message for a non-Error start failure', async () => {
    vi.mocked(api.startWorkflow).mockRejectedValue('boom');
    const { result } = setup();
    await act(() => result.current.handleStartWorkflow());
    expect(result.current.startError).toBe('Failed to start workflow');
  });

  it('never starts without a selected project', async () => {
    const { result } = setup({ selectedProject: null });
    await act(() => result.current.handleStartWorkflow());
    expect(api.startWorkflow).not.toHaveBeenCalled();
    await act(() => result.current.handleInitSpecFolder());
    expect(api.initSpecFolder).not.toHaveBeenCalled();
  });

  it('marks a run as ended and clears it for a new run', async () => {
    const { result } = setup();
    await act(() => result.current.handleStartWorkflow());
    act(() => result.current.markRunEnded());
    expect(result.current.runEnded).toBe(true);
    act(() => result.current.handleNewRun());
    expect(result.current.activeRun).toBeNull();
    expect(result.current.runEnded).toBe(false);
  });

  it('resets all run state', async () => {
    vi.mocked(api.startWorkflow).mockRejectedValueOnce(new Error('x'));
    const { result } = setup();
    await act(() => result.current.handleStartWorkflow());
    act(() => result.current.reset());
    expect(result.current.startError).toBeNull();
    expect(result.current.activeRun).toBeNull();
    expect(result.current.initResult).toBeNull();
    expect(result.current.initError).toBeNull();
  });

  describe('spec folder', () => {
    it('initialises the spec folder after pre-flight', async () => {
      vi.mocked(api.initSpecFolder).mockResolvedValue({ created: true } as never);
      const { result } = setup();
      await act(() => result.current.handleInitSpecFolder());
      expect(api.initSpecFolder).toHaveBeenCalledWith('p1');
      expect(result.current.initResult).toEqual({ created: true });
      expect(result.current.buttonState).toBe('idle');
    });

    it('reports an init failure, with a fallback for non-Errors', async () => {
      vi.mocked(api.initSpecFolder).mockRejectedValueOnce(new Error('disk full'));
      const { result } = setup();
      await act(() => result.current.handleInitSpecFolder());
      expect(result.current.initError).toBe('disk full');
      expect(result.current.buttonState).toBe('error');

      vi.mocked(api.initSpecFolder).mockRejectedValueOnce('boom');
      await act(() => result.current.handleInitSpecFolder());
      expect(result.current.initError).toBe('Spec folder initialization failed');
    });

    it('does not initialise when pre-flight fails', async () => {
      vi.mocked(api.checkWorkflowPreflight).mockResolvedValue({
        passed: false,
        errors: [{ code: 'TIMEOUT', message: 't' }],
      });
      const { result } = setup();
      await act(() => result.current.handleInitSpecFolder());
      expect(api.initSpecFolder).not.toHaveBeenCalled();
    });

    it('prefers wf-spec-create as the spec-creation workflow', () => {
      const { result } = setup({ projectWorkflows: [prompted, specCreate] });
      expect(result.current.specCreateWorkflow).toBe(specCreate);
    });

    it('falls back to any prompt-requiring workflow', () => {
      const { result } = setup();
      expect(result.current.specCreateWorkflow).toBe(prompted);
    });

    it('opens the prompt for the spec-creation workflow when there is one', () => {
      const { result } = setup({ projectWorkflows: [plain, specCreate] });
      act(() => result.current.handleCreateSpecWithPrompt());
      expect(result.current.promptWorkflow).toBe(specCreate);
    });

    it('does nothing when there is no spec-creation workflow', () => {
      const { result } = setup({ projectWorkflows: [plain] });
      act(() => result.current.handleCreateSpecWithPrompt());
      expect(result.current.promptWorkflow).toBeNull();
    });
  });
});
