import { Suspense, useState } from 'react';
import { useNavigate } from 'react-router';
import { useProjectWorkflows } from '@openjcockpit/workflow/queries';
import type { Project } from '@openjcockpit/api';
import { WorkflowPromptDialog } from '../../lazyViews';
import { ROUTES } from '../../routes';
import { useRunLauncher } from '../../useRunLauncher';
import { useProjectSpecsQuery } from '../DashboardView/useDashboardQueries';
import { FlowPanel } from '../FlowPanel/FlowPanel';
import { SpecPanel } from '../SpecPanel/SpecPanel';
import styles from './SpecWorkspace.module.scss';

/**
 * The spec list next to the workflow launcher and preview of the chosen spec. Used by the
 * project dashboard and as a Home widget; the caller supplies the surrounding panel.
 * Mount with `key={project.id}` so the selection resets per project.
 */
export function SpecWorkspace({ project }: { project: Project }) {
  const navigate = useNavigate();
  const specsQuery = useProjectSpecsQuery(project.id);
  const projectWorkflows = useProjectWorkflows(project.name);
  const [selectedSpecId, setSelectedSpecId] = useState('');
  const [chosenWorkflowId, setSelectedWorkflowId] = useState('');

  const specs = specsQuery.data?.specs ?? [];
  const specInitStatus = specsQuery.data?.specInitStatus ?? null;
  const specsLoading = specsQuery.isLoading;
  const selected =
    specs.find((s) => s.id === selectedSpecId) ?? specs.find((s) => s.selected) ?? specs[0];
  const specsError = specsQuery.isError && !specsQuery.data;
  const hasSpecs = !specsLoading && specs.length > 0;

  // Falls back to the first workflow while the chosen one is unset or no longer in the project.
  const selectedWorkflowId = projectWorkflows.some((w) => w.id === chosenWorkflowId)
    ? chosenWorkflowId
    : (projectWorkflows[0]?.id ?? chosenWorkflowId);

  const launcher = useRunLauncher({
    selectedProject: project,
    projectWorkflows,
    selectedWorkflowId,
    selectedSpec: selected,
    onStarted: (workflow, runId) =>
      void navigate(`${ROUTES.dashboardRun}/${runId}?workflow=${encodeURIComponent(workflow.id)}`),
  });
  const { buttonState, promptWorkflow } = launcher;

  return (
    <div className={styles.workspace}>
      {promptWorkflow && (
        <Suspense fallback={null}>
          <WorkflowPromptDialog
            workflowName={promptWorkflow.name}
            busy={buttonState === 'preflight' || buttonState === 'starting'}
            onSubmit={(prompt) => {
              void launcher.handlePromptSubmit(prompt);
            }}
            onCancel={launcher.cancelPrompt}
          />
        </Suspense>
      )}

      <SpecPanel
        specs={specs}
        loading={specsLoading}
        error={specsError}
        onRetry={() => void specsQuery.refetch()}
        selectedSpecId={selected?.id ?? ''}
        onSelect={setSelectedSpecId}
        onGoToWorkflow={() => void navigate(ROUTES.workflow)}
        onViewAll={() => void navigate(ROUTES.specFiles)}
      />

      <FlowPanel
        launcher={launcher}
        specsLoading={specsLoading}
        specsError={specsError}
        hasSpecs={hasSpecs}
        selectedFileName={selected?.fileName}
        selectedContent={selected?.content}
        projectWorkflows={projectWorkflows}
        selectedWorkflowId={selectedWorkflowId}
        onWorkflowChange={setSelectedWorkflowId}
        specInitStatus={specInitStatus}
        onReloadSpecs={() => void specsQuery.refetch()}
        onOpenProjectSettings={() => void navigate(ROUTES.projectSettings)}
      />
    </div>
  );
}
