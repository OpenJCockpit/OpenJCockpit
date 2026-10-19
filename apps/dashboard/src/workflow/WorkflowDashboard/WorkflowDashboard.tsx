import { useEffect, useState } from 'react';
import type { Project } from '../../types';
import { WorkflowOverview } from '../WorkflowOverview/WorkflowOverview';
import { WorkflowConfiguration } from '../WorkflowConfiguration/WorkflowConfiguration';
import { WorkflowDesign } from '../WorkflowDesign/WorkflowDesign';
import { WorkflowExecution } from '../WorkflowExecution/WorkflowExecution';
import { getWorkflow } from '../workflowApi';
import type { WorkflowDefinition } from '../workflowTypes';
import { AppShell, AppHeader } from '../../components/AppShell/AppShell';
import { Eyebrow } from '../../ui/Panel/Panel';
import { feedbackErrorClass } from '../../ui/Feedback/Feedback';
import { Tab, TabNav } from '../../ui/Tabs/Tabs';
import styles from './WorkflowDashboard.module.scss';

type WorkflowTab = 'overview' | 'configuration' | 'design' | 'execution';

interface Props {
  project: Project | null;
  onBack: () => void;
  /** Open straight on the Execution tab for this run (e.g. from the spec queue). */
  initialExecution?: { workflowId: string; runId: string };
}

export function WorkflowDashboard({ project, onBack, initialExecution }: Props) {
  const [tab, setTab] = useState<WorkflowTab>(initialExecution ? 'execution' : 'overview');
  const [executionWorkflow, setExecutionWorkflow] = useState<WorkflowDefinition | null>(null);
  const [executionRunId, setExecutionRunId] = useState<string | null>(
    initialExecution?.runId ?? null,
  );
  const [initialError, setInitialError] = useState<string | null>(null);

  const initialWorkflowId = initialExecution?.workflowId;
  useEffect(() => {
    if (!initialWorkflowId) return;
    let live = true;
    setInitialError(null);
    getWorkflow(initialWorkflowId).then(
      (workflow) => live && setExecutionWorkflow(workflow),
      (e) => live && setInitialError(e instanceof Error ? e.message : 'Failed to load workflow'),
    );
    return () => {
      live = false;
    };
  }, [initialWorkflowId]);

  function handleWorkflowStarted(workflow: WorkflowDefinition, runId: string) {
    setExecutionWorkflow(workflow);
    setExecutionRunId(runId);
    setTab('execution');
  }

  function handleViewExecutionTab(workflow: WorkflowDefinition) {
    setExecutionWorkflow(workflow);
    setTab('execution');
  }

  return (
    <AppShell>
      <AppHeader onBack={onBack} backTitle="Back to dashboard" />

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
