import { useState, type ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { getWorkflow, queryKeys } from '@openjcockpit/api';
import type { Project, WorkflowDefinition } from '@openjcockpit/api';
import { useTranslation } from '@openjcockpit/i18n';
import { WorkflowOverview } from '../WorkflowOverview/WorkflowOverview';
import { WorkflowConfiguration } from '../WorkflowConfiguration/WorkflowConfiguration';
import { WorkflowDesign } from '../WorkflowDesign/WorkflowDesign';
import { WorkflowExecution } from '../WorkflowExecution/WorkflowExecution';
import {
  AppShell,
  AppHeader,
  Eyebrow,
  cx,
  feedbackErrorClass,
  pageClass,
  pageHeaderClass,
  Tab,
  TabNav,
} from '@openjcockpit/ui';
import styles from './WorkflowDashboard.module.scss';

type WorkflowTab = 'overview' | 'configuration' | 'design' | 'execution';

interface Props {
  project: Project | null;
  onBack: () => void;
  /** Open straight on the Execution tab for this run (e.g. from the spec queue). */
  initialExecution?: { workflowId: string; runId: string };
  /** Replaces the default back-button header, e.g. with the app's shared top menu. */
  header?: ReactNode;
}

export function WorkflowDashboard({ project, onBack, initialExecution, header }: Props) {
  const { t } = useTranslation('workflow');
  const [tab, setTab] = useState<WorkflowTab>(initialExecution ? 'execution' : 'overview');
  const [pickedWorkflow, setPickedWorkflow] = useState<WorkflowDefinition | null>(null);
  const [executionRunId, setExecutionRunId] = useState<string | null>(
    initialExecution?.runId ?? null,
  );

  const initialWorkflowId = initialExecution?.workflowId;
  const initialWorkflowQuery = useQuery({
    queryKey: queryKeys.workflow(initialWorkflowId as string),
    queryFn: () => getWorkflow(initialWorkflowId as string),
    enabled: Boolean(initialWorkflowId),
  });
  // A workflow chosen in the UI wins; until then the run's own workflow (once loaded) is shown.
  const executionWorkflow = pickedWorkflow ?? initialWorkflowQuery.data ?? null;
  const initialError = initialWorkflowQuery.isError
    ? initialWorkflowQuery.error instanceof Error
      ? initialWorkflowQuery.error.message
      : t('dashboard.workflowLoadFailed')
    : null;

  function handleWorkflowStarted(workflow: WorkflowDefinition, runId: string) {
    setPickedWorkflow(workflow);
    setExecutionRunId(runId);
    setTab('execution');
  }

  function handleViewExecutionTab(workflow: WorkflowDefinition) {
    setPickedWorkflow(workflow);
    setTab('execution');
  }

  return (
    <AppShell>
      {header ?? <AppHeader onBack={onBack} backTitle={t('dashboard.backTitle')} />}

      <main className={pageClass}>
        <div className={cx(pageHeaderClass, styles.workflowDashboardHeader)}>
          <div>
            <Eyebrow>{t('dashboard.eyebrow')}</Eyebrow>
            <h1>{project ? project.name : t('dashboard.allProjects')}</h1>
          </div>
          <TabNav aria-label={t('dashboard.tabsLabel')}>
            <Tab active={tab === 'overview'} onClick={() => setTab('overview')}>
              {t('dashboard.tabs.overview')}
            </Tab>
            <Tab active={tab === 'configuration'} onClick={() => setTab('configuration')}>
              {t('dashboard.tabs.configuration')}
            </Tab>
            <Tab active={tab === 'design'} onClick={() => setTab('design')}>
              {t('dashboard.tabs.design')}
            </Tab>
            <Tab active={tab === 'execution'} onClick={() => setTab('execution')}>
              {t('dashboard.tabs.execution')}
            </Tab>
          </TabNav>
        </div>

        {tab === 'overview' && (
          <WorkflowOverview
            project={project}
            onWorkflowStarted={handleWorkflowStarted}
            onViewExecutionTab={handleViewExecutionTab}
          />
        )}
        {tab === 'configuration' && <WorkflowConfiguration project={project} />}
        {tab === 'design' && <WorkflowDesign />}
        {tab === 'execution' && initialError && !executionWorkflow && (
          <p className={feedbackErrorClass} role="alert">
            {t('dashboard.runLoadFailed', { error: initialError })}
          </p>
        )}
        {tab === 'execution' && (
          <WorkflowExecution
            workflow={executionWorkflow}
            runId={executionRunId}
            onRestartRequested={() => setTab('overview')}
          />
        )}
      </main>
    </AppShell>
  );
}
