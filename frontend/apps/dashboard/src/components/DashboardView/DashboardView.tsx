import { Suspense, useState } from 'react';
import { useNavigate } from 'react-router';
import { useTranslation } from '@openjcockpit/i18n';
import { AppShell, StatusMessage } from '@openjcockpit/ui';
import { useProjectWorkflows } from '@openjcockpit/workflow/queries';
import type { Project } from '@openjcockpit/api';
import { WorkflowPromptDialog } from '../../lazyViews';
import { ROUTES } from '../../routes';
import { useRunLauncher } from '../../useRunLauncher';
import { CustomerHero } from '../CustomerHero/CustomerHero';
import { FlowPanel } from '../FlowPanel/FlowPanel';
import { SiteHeader } from '../SiteHeader/SiteHeader';
import { SpecPanel } from '../SpecPanel/SpecPanel';
import { StatusBar } from '../StatusBar/StatusBar';
import { EvidencePanel } from '../WorkspacePanels/EvidencePanel';
import { QualityPanel } from '../WorkspacePanels/QualityPanel';
import { useProjectSpecsQuery, useWorkspaceQuery } from './useDashboardQueries';
import styles from './DashboardView.module.scss';

interface Props {
  projects: Project[];
  project: Project;
  onProjectChange: (projectId: string) => void;
  onLogout: () => void;
}

/** The project dashboard. Mounted with `key={project.id}` so all local state resets per project. */
export function DashboardView({ projects, project, onProjectChange, onLogout }: Props) {
  const { t } = useTranslation('dashboard');
  const navigate = useNavigate();
  const workspaceQuery = useWorkspaceQuery(project);
  const specsQuery = useProjectSpecsQuery(project.id);
  const projectWorkflows = useProjectWorkflows(project.name);
  const [selectedSpecId, setSelectedSpecId] = useState('');
  const [chosenWorkflowId, setSelectedWorkflowId] = useState('');

  const workspace = workspaceQuery.data;
  const specs = specsQuery.data?.specs ?? [];
  const specInitStatus = specsQuery.data?.specInitStatus ?? null;
  const specsLoading = specsQuery.isFetching;
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
  });
  const { buttonState, promptWorkflow } = launcher;

  return (
    <AppShell>
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

      <SiteHeader
        active="platform"
        projects={projects}
        selectedProject={project}
        onProjectChange={onProjectChange}
        onLogout={onLogout}
      />

      <main className={styles.workspace}>
        {workspace ? (
          <CustomerHero selectedProject={project} workspace={workspace} />
        ) : (
          <StatusMessage
            variant={workspaceQuery.isError ? 'error' : 'loading'}
            data-testid="workspace-status"
          >
            {workspaceQuery.isError ? t('workspace.failed') : t('workspace.loading')}
          </StatusMessage>
        )}

        <section className={styles.dashboardGrid}>
          <SpecPanel
            specs={specs}
            loading={specsLoading}
            error={specsError}
            onRetry={() => void specsQuery.refetch()}
            selectedSpecId={selected?.id ?? ''}
            selected={selected}
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
            projectWorkflows={projectWorkflows}
            selectedWorkflowId={selectedWorkflowId}
            onWorkflowChange={setSelectedWorkflowId}
            specInitStatus={specInitStatus}
            onReloadSpecs={() => void specsQuery.refetch()}
            onOpenProjectSettings={() => void navigate(ROUTES.settings)}
          />

          {workspace && (
            <>
              <QualityPanel controls={workspace.qualityControls} />
              <EvidencePanel events={workspace.evidenceEvents} />
            </>
          )}
        </section>
      </main>

      {workspace && <StatusBar footer={workspace.footer} />}
    </AppShell>
  );
}
