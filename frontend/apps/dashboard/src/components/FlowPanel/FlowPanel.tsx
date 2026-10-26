import type { SpecInitStatus, WorkflowDefinition } from '@openjcockpit/api';
import { WorkflowExecution } from '@openjcockpit/workflow';
import type { useRunLauncher } from '../../useRunLauncher';
import { PreflightAlert } from '../PreflightAlert/PreflightAlert';
import {
  StatusChip,
  Eyebrow,
  Panel,
  PanelHeader,
  Button,
  feedbackErrorClass,
} from '@openjcockpit/ui';
import { SpecInitLauncher } from './SpecInitLauncher';
import { SpecInitOffered } from './SpecInitOffered';
import { SpecInitPending } from './SpecInitPending';
import { SpecTemplatePresent } from './SpecTemplatePresent';
import { WorkflowLauncher } from './WorkflowLauncher';
import styles from './FlowPanel.module.scss';

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
    startError,
    initResult,
    initError,
    specCreateWorkflow,
  } = launcher;

  function renderTitle() {
    if (specsLoading) return 'Loading specs…';
    return hasSpecs ? selectedFileName : 'No specs found';
  }

  function renderActiveRun() {
    return (
      <>
        <WorkflowExecution
          workflow={activeRun!.workflow}
          runId={activeRun!.runId}
          onRunEnded={launcher.markRunEnded}
          showHistory={false}
        />
        {runEnded && (
          <Button className={styles.workflowLauncherReset} onClick={launcher.handleNewRun}>
            ↺ Start new workflow
          </Button>
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
    <Panel className={styles.flowPanel}>
      <PanelHeader className={styles.flowHeader}>
        <div>
          <Eyebrow>{hasSpecs ? 'Current spec' : 'Spec folder'}</Eyebrow>
          <h2>{renderTitle()}</h2>
        </div>
        <div className={styles.flowHeaderActions}>
          {activeRun && !runEnded && <StatusChip label="Active" kind="active" />}
        </div>
      </PanelHeader>

      {preflightErrors && (
        <PreflightAlert
          errors={preflightErrors}
          onDismiss={launcher.dismissPreflightErrors}
          onOpenProjectSettings={onOpenProjectSettings}
        />
      )}

      {!activeRun && startError && (
        <p className={feedbackErrorClass} role="alert" data-testid="start-error">
          {startError}
        </p>
      )}

      {renderBody()}
    </Panel>
  );
}
