import { useState } from 'react';
import { checkWorkflowPreflight, initSpecFolder, startWorkflow } from '@openjcockpit/api';
import type {
  PreflightError,
  Project,
  SpecFile,
  SpecInitResult,
  WorkflowDefinition,
} from '@openjcockpit/api';

export type LauncherButtonState = 'idle' | 'preflight' | 'starting' | 'error';

interface Options {
  selectedProject: Project | null;
  projectWorkflows: WorkflowDefinition[];
  selectedWorkflowId: string;
  selectedSpec: SpecFile | undefined;
  /** Called once a run has started, so the host can open the run on its own screen. */
  onStarted: (workflow: WorkflowDefinition, runId: string) => void;
}

/** Pre-flight, start and track a workflow run (or the spec-folder init) for the selected project. */
export function useRunLauncher({
  selectedProject,
  projectWorkflows,
  selectedWorkflowId,
  selectedSpec,
  onStarted,
}: Options) {
  const [buttonState, setButtonState] = useState<LauncherButtonState>('idle');
  const [preflightErrors, setPreflightErrors] = useState<PreflightError[] | null>(null);
  const [startError, setStartError] = useState<string | null>(null);
  const [initResult, setInitResult] = useState<SpecInitResult | null>(null);
  const [initError, setInitError] = useState<string | null>(null);
  const [promptWorkflow, setPromptWorkflow] = useState<WorkflowDefinition | null>(null);

  // The default spec-creation workflow (global group) — used when the
  // repository already has a .specify folder but no specs yet.
  const specCreateWorkflow =
    projectWorkflows.find((w) => w.id === 'wf-spec-create') ??
    projectWorkflows.find((w) => w.promptRequired);

  /** Clears all run state, e.g. when another project is selected. */
  function reset() {
    setStartError(null);
    setInitResult(null);
    setInitError(null);
  }

  function cancelPrompt() {
    setPromptWorkflow(null);
    setButtonState('idle');
  }

  async function runPreflight(): Promise<boolean> {
    if (!selectedProject) return false;
    setButtonState('preflight');
    setPreflightErrors(null);
    try {
      const preflight = await checkWorkflowPreflight(selectedProject.id);
      if (!preflight.passed) {
        setPreflightErrors(preflight.errors);
        setButtonState('idle');
        return false;
      }
      return true;
    } catch {
      setPreflightErrors([
        { code: 'UNKNOWN_ERROR', message: 'Pre-flight check could not be completed.' },
      ]);
      setButtonState('idle');
      return false;
    }
  }

  async function launchWorkflow(workflow: WorkflowDefinition, input: { prompt?: string }) {
    if (!(await runPreflight())) {
      setPromptWorkflow(null);
      return;
    }
    setButtonState('starting');
    setStartError(null);
    try {
      const result = await startWorkflow(workflow.id, {
        ...input,
        specFile: selectedSpec?.fileName,
        repositoryUrl: selectedProject?.gitUrl,
        projectId: selectedProject?.id,
      });
      setPromptWorkflow(null);
      setButtonState('idle');
      onStarted(workflow, result.executionId);
    } catch (error) {
      setStartError(error instanceof Error ? error.message : 'Failed to start workflow');
      setButtonState('error');
    }
  }

  async function handleStartWorkflow() {
    const workflow = projectWorkflows.find((w) => w.id === selectedWorkflowId);
    if (!workflow) return;
    if (workflow.promptRequired) {
      setPromptWorkflow(workflow);
      return;
    }
    await launchWorkflow(workflow, {});
  }

  async function handlePromptSubmit(prompt: string) {
    if (!promptWorkflow) return;
    await launchWorkflow(promptWorkflow, { prompt });
  }

  function handleCreateSpecWithPrompt() {
    if (specCreateWorkflow) setPromptWorkflow(specCreateWorkflow);
  }

  async function handleInitSpecFolder() {
    if (!selectedProject || !(await runPreflight())) return;

    setButtonState('starting');
    setInitError(null);
    try {
      const result = await initSpecFolder(selectedProject.id);
      setInitResult(result);
      setButtonState('idle');
    } catch (error) {
      setInitError(error instanceof Error ? error.message : 'Spec folder initialization failed');
      setButtonState('error');
    }
  }

  return {
    buttonState,
    preflightErrors,
    dismissPreflightErrors: () => setPreflightErrors(null),
    startError,
    initResult,
    initError,
    promptWorkflow,
    specCreateWorkflow,
    reset,
    cancelPrompt,
    handleStartWorkflow,
    handlePromptSubmit,
    handleCreateSpecWithPrompt,
    handleInitSpecFolder,
  };
}
