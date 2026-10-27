import { useState, type ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { getWorkflow, queryKeys } from '@openjcockpit/api';
import type { Project, WorkflowDefinition } from '@openjcockpit/api';
import { WorkflowOverview } from '../WorkflowOverview/WorkflowOverview';
import { WorkflowConfiguration } from '../WorkflowConfiguration/WorkflowConfiguration';
import { WorkflowDesign } from '../WorkflowDesign/WorkflowDesign';
import { WorkflowExecution } from '../WorkflowExecution/WorkflowExecution';
import { AppShell, AppHeader, Eyebrow, feedbackErrorClass, Tab, TabNav } from '@openjcockpit/ui';
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
      : 'Failed to load workflow'
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
      {header ?? <AppHeader onBack={onBack} backTitle="Back to dashboard" />}

      <main className={styles.workflowDashboard}>
        <div className={styles.workflowDashboardHeader}>
          <div>
            <Eyebrow>Workflow Design &amp; Execution</Eyebrow>
            <h1>{project ? project.name : 'All projects'}</h1>
          </div>
          <TabNav aria-label="Workflow secties">
            <Tab active={tab === 'overview'} onClick={() => setTab('overview')}>
              Overview
            </Tab>
            <Tab active={tab === 'configuration'} onClick={() => setTab('configuration')}>
              Configuration
            </Tab>
            <Tab active={tab === 'design'} onClick={() => setTab('design')}>
              Design
            </Tab>
            <Tab active={tab === 'execution'} onClick={() => setTab('execution')}>
              Workflow Execution
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
            The workflow of this run could not be loaded — {initialError}
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
