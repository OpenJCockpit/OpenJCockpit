import './WorkflowDashboard.scss';
import { useEffect, useState } from 'react';
import type { Project } from '../../types';
import { WorkflowOverview } from '../WorkflowOverview/WorkflowOverview';
import { WorkflowConfiguration } from '../WorkflowConfiguration/WorkflowConfiguration';
import { WorkflowDesign } from '../WorkflowDesign/WorkflowDesign';
import { WorkflowExecution } from '../WorkflowExecution/WorkflowExecution';
import { getWorkflow } from '../workflowApi';
import type { WorkflowDefinition } from '../workflowTypes';

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
    <div className="app-shell">
      <div className="neural-bg" aria-hidden="true" />
      <div className="orb orb--left" aria-hidden="true" />
      <div className="orb orb--right" aria-hidden="true" />

      <header className="site-header">
        <div className="brand">
          <img src="/openjcockpit-logo.png" alt="OpenJCockpit" className="brand-logo" />
        </div>
        <div className="header-actions">
          <button className="back-button" onClick={onBack} title="Back to dashboard">
            ← Back
          </button>
        </div>
      </header>

      <main className="workflow-dashboard">
        <div className="workflow-dashboard-header">
          <div>
            <span className="eyebrow">Workflow Design &amp; Execution</span>
            <h1>{project ? project.name : 'All projects'}</h1>
          </div>
          <nav className="workflow-tab-nav" aria-label="Workflow secties">
            <button
              className={`workflow-tab ${tab === 'overview' ? 'workflow-tab--active' : ''}`}
              onClick={() => setTab('overview')}
            >
              Overview
            </button>
            <button
              className={`workflow-tab ${tab === 'configuration' ? 'workflow-tab--active' : ''}`}
              onClick={() => setTab('configuration')}
            >
              Configuration
            </button>
            <button
              className={`workflow-tab ${tab === 'design' ? 'workflow-tab--active' : ''}`}
              onClick={() => setTab('design')}
            >
              Design
            </button>
            <button
              className={`workflow-tab ${tab === 'execution' ? 'workflow-tab--active' : ''}`}
              onClick={() => setTab('execution')}
            >
              Workflow Execution
            </button>
          </nav>
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
          <p className="settings-error" role="alert">
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
    </div>
  );
}
