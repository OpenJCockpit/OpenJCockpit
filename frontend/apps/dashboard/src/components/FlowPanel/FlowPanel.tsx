import type { SpecInitStatus, WorkflowDefinition } from '@openjcockpit/api';
import { useTranslation } from '@openjcockpit/i18n';
import type { useRunLauncher } from '../../useRunLauncher';
import { PreflightAlert } from '../PreflightAlert/PreflightAlert';
import { Eyebrow, PanelHeader, Button, feedbackErrorClass } from '@openjcockpit/ui';
import { specTitle } from '../SpecPanel/specMeta';
import { SpecPreview } from './SpecPreview';
import { SpecInitLauncher } from './SpecInitLauncher';
import { SpecInitOffered } from './SpecInitOffered';
import { SpecInitPending } from './SpecInitPending';
import { SpecTemplatePresent } from './SpecTemplatePresent';
import { WorkflowLauncher } from './WorkflowLauncher';
import styles from './FlowPanel.module.scss';

interface Props {
  launcher: ReturnType<typeof useRunLauncher>;
  specsLoading: boolean;
  specsError?: boolean;
  hasSpecs: boolean;
  selectedFileName: string | undefined;
  selectedContent?: string;
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
  specsError = false,
  hasSpecs,
  selectedFileName,
  selectedContent,
  projectWorkflows,
  selectedWorkflowId,
  onWorkflowChange,
  specInitStatus,
  onReloadSpecs,
  onOpenProjectSettings,
}: Props) {
  const { t } = useTranslation('dashboard');
  const { buttonState, preflightErrors, startError, initResult, initError, specCreateWorkflow } =
    launcher;

  const showPreview = hasSpecs && !specsLoading && !specsError && !!selectedContent;

  function renderTitle() {
    if (specsLoading) return t('flow.loadingSpecs');
    if (specsError) return t('flow.specsFailedTitle');
    if (!hasSpecs) return t('flow.noSpecsFound');
    return selectedFileName
      ? specTitle({ fileName: selectedFileName, content: selectedContent })
      : '';
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
    if (specsLoading) return null;
    if (specsError) {
      return (
        <div role="alert" data-testid="flow-specs-error" className={styles.flowError}>
          <p className={feedbackErrorClass}>{t('flow.specsFailed')}</p>
          <Button onClick={onReloadSpecs}>{t('flow.reloadSpecs')}</Button>
        </div>
      );
    }
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
    <div className={styles.flowPanel}>
      <PanelHeader className={styles.flowHeader}>
        <div>
          <Eyebrow>{hasSpecs ? t('flow.currentSpec') : t('flow.specFolder')}</Eyebrow>
          <h2>{renderTitle()}</h2>
        </div>
      </PanelHeader>

      {preflightErrors && (
        <PreflightAlert
          errors={preflightErrors}
          onDismiss={launcher.dismissPreflightErrors}
          onOpenProjectSettings={onOpenProjectSettings}
        />
      )}

      {startError && (
        <p className={feedbackErrorClass} role="alert" data-testid="start-error">
          {startError}
        </p>
      )}

      {showPreview ? (
        <div className={styles.flowBody}>
          {renderBody()}
          <div className={styles.specPreview}>
            <SpecPreview content={selectedContent} />
          </div>
        </div>
      ) : (
        renderBody()
      )}
    </div>
  );
}
