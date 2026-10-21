import { Suspense, useState } from 'react';
import { useNavigate } from 'react-router';
import { AppShell, StatusMessage, ToolbarButton } from '@openjcockpit/ui';
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
import { PullRequestPanel } from '../WorkspacePanels/PullRequestPanel';
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
        projects={projects}
        selectedProject={project}
        onProjectChange={onProjectChange}
        onWorkflow={() => void navigate(ROUTES.workflow)}
        onSpecFiles={() => void navigate(ROUTES.specFiles)}
        onSpecQueue={() => void navigate(ROUTES.specQueue)}
        onSkillsHub={() => void navigate(ROUTES.skillsHub)}
        onSettings={() => void navigate(ROUTES.settings)}
        onHub={() => void navigate(ROUTES.landing)}
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
            {workspaceQuery.isError ? 'Failed to load the workspace.' : 'Loading workspace...'}
          </StatusMessage>
        )}

        <section className={styles.topTools}>
          <div className={styles.search}>
            Search workspace... <span>⌕</span>
          </div>
          <ToolbarButton variant="small">Filters</ToolbarButton>
          <div className={styles.topToolsSpacer} />
          <ToolbarButton variant="small">⟳ Refresh</ToolbarButton>
          <span className={styles.lastUpdate}>● Last update: 13:24:11</span>
        </section>

        <section className={styles.dashboardGrid}>
          <SpecPanel
            specs={specs}
            loading={specsLoading}
            selectedSpecId={selected?.id ?? ''}
            selected={selected}
            onSelect={setSelectedSpecId}
            onGoToWorkflow={() => void navigate(ROUTES.workflow)}
            onViewAll={() => void navigate(ROUTES.specFiles)}
          />

          <FlowPanel
            launcher={launcher}
            specsLoading={specsLoading}
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
              <PullRequestPanel pullRequests={workspace.pullRequests} />
              <EvidencePanel events={workspace.evidenceEvents} />
            </>
          )}
        </section>
      </main>

      {workspace && <StatusBar footer={workspace.footer} />}
    </AppShell>
  );
}
