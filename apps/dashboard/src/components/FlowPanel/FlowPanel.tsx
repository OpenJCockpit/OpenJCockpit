import type { SpecInitStatus } from '../../types';
import type { WorkflowDefinition } from '../../workflow/workflowTypes';
import { WorkflowExecution } from '../../workflow/WorkflowExecution/WorkflowExecution';
import type { useRunLauncher } from '../../useRunLauncher';
import { PreflightAlert } from '../PreflightAlert/PreflightAlert';
import { StatusChip } from '../StatusChip/StatusChip';
import { SpecInitLauncher } from './SpecInitLauncher';
import { SpecInitOffered } from './SpecInitOffered';
import { SpecInitPending } from './SpecInitPending';
import { SpecTemplatePresent } from './SpecTemplatePresent';
import { WorkflowLauncher } from './WorkflowLauncher';

interface Props {
  launcher: ReturnType<typeof useRunLauncher>;
  specsLoading: boolean;
  hasSpecs: boolean;
  selectedFileName: string | undefined;
  projectWorkflows: WorkflowDefinition[];
  selectedWorkflowId: string;
  onWorkflowChange: (id: string) => void;
  specInitStatus: SpecInitStatus | null;
  onReloadSpecs: () => void;
  onOpenProjectSettings: () => void;
}

export function FlowPanel({
  launcher,
  specsLoading,
  hasSpecs,
  selectedFileName,
  projectWorkflows,
  selectedWorkflowId,
  onWorkflowChange,
  specInitStatus,
  onReloadSpecs,
  onOpenProjectSettings,
}: Props) {
  const {
    buttonState,
    preflightErrors,
    activeRun,
    runEnded,
    initResult,
    initError,
    specCreateWorkflow,
  } = launcher;

  function renderTitle() {
    if (specsLoading) return 'Loading specs…';
    return hasSpecs ? selectedFileName : 'No specs found';
  }

  function renderActiveRun() {
    if (!activeRun) return null;
    return (
      <>
        <WorkflowExecution
          workflow={activeRun.workflow}
          runId={activeRun.runId}
          onRunEnded={launcher.markRunEnded}
          showHistory={false}
        />
        {runEnded && (
          <button className="button workflow-launcher-reset" onClick={launcher.handleNewRun}>
            ↺ Start new workflow
          </button>
        )}
      </>
    );
  }

  function renderSpecInit() {
    if (initResult) {
      return <SpecInitOffered result={initResult} onReload={onReloadSpecs} />;
    }
    if (specInitStatus?.templateExists) {
      return (
        <SpecTemplatePresent
          specCreateWorkflow={specCreateWorkflow}
          onCreateSpec={launcher.handleCreateSpecWithPrompt}
        />
      );
    }
    if (specInitStatus?.pending) {
      return <SpecInitPending status={specInitStatus} onReload={onReloadSpecs} />;
    }
    return (
      <SpecInitLauncher
        buttonState={buttonState}
        error={initError}
        onStart={() => {
          void launcher.handleInitSpecFolder();
        }}
      />
    );
  }

  function renderBody() {
    if (activeRun) return renderActiveRun();
    if (specsLoading) return null;
    if (!hasSpecs) return renderSpecInit();
    return (
      <WorkflowLauncher
        workflows={projectWorkflows}
        selectedWorkflowId={selectedWorkflowId}
        onWorkflowChange={onWorkflowChange}
        buttonState={buttonState}
        onStart={() => {
          void launcher.handleStartWorkflow();
        }}
      />
    );
  }

  return (
    <section className="panel flow-panel">
      <header className="panel-header flow-header">
        <div>
          <span className="eyebrow">{hasSpecs ? 'Current spec' : 'Spec folder'}</span>
          <h2>{renderTitle()}</h2>
        </div>
        <div className="flow-header-actions">
          {activeRun && !runEnded && <StatusChip label="Active" kind="active" />}
        </div>
      </header>

      {preflightErrors && (
        <PreflightAlert
          errors={preflightErrors}
          onDismiss={launcher.dismissPreflightErrors}
          onOpenProjectSettings={onOpenProjectSettings}
        />
      )}

      {renderBody()}
    </section>
  );
}
