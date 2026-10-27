import { referenceLabel, runStatusKind } from '@openjcockpit/api';
import type { AgentDefinition, Project, WorkflowDefinition } from '@openjcockpit/api';
import {
  StatusChip,
  Eyebrow,
  Panel,
  fieldErrorClass,
  fieldHintClass,
  Button,
  StatusMessage,
} from '@openjcockpit/ui';
import { WorkflowPromptDialog } from '../WorkflowPromptDialog/WorkflowPromptDialog';
import { useWorkflowOverview } from './useWorkflowOverview';
import styles from './WorkflowOverview.module.scss';

interface Props {
  project: Project | null;
  onWorkflowStarted?: (workflow: WorkflowDefinition, runId: string) => void;
  onViewExecutionTab?: (workflow: WorkflowDefinition) => void;
}

function triggerSummary(workflow: WorkflowDefinition): string {
  const triggers: string[] = [];
  if (workflow.trigger?.dashboardButtonEnabled) triggers.push('Dashboard button');
  if (workflow.trigger?.hermesSignalEnabled) {
    triggers.push(`Hermes signal (${workflow.trigger.hermesSignalType ?? 'any'})`);
  }
  if (workflow.trigger?.fileDeliveryEnabled) triggers.push('File delivery');
  return triggers.length > 0 ? triggers.join(', ') : 'None configured';
}

function agentSummary(
  agentIds: string[],
  index: Map<string, AgentDefinition>,
  degraded: boolean,
): string {
  if (agentIds.length === 0) return 'None';
  if (degraded) {
    return agentIds.join(', ');
  }
  // in the workflow's execution order; references missing from the catalog stay visible
  const parts = agentIds.map((id) => {
    const agent = index.get(id);
    return agent ? agent.name : `${referenceLabel(id)} (unrecognised)`;
  });
  return parts.join(', ');
}

export function WorkflowOverview({ project, onWorkflowStarted, onViewExecutionTab }: Props) {
  const {
    loaded,
    visibleWorkflows,
    agentIndex,
    listError,
    catalogueError,
    opaEnabled,
    decisionSummaries,
    startingId,
    exportingId,
    feedback,
    promptWorkflow,
    setPromptWorkflow,
    handleStart,
    launchWorkflow,
    handleExportDecisionLogs,
  } = useWorkflowOverview(project, onWorkflowStarted);

  if (!loaded) {
    return <StatusMessage variant="loading">Loading workflows...</StatusMessage>;
  }

  return (
    <section className="workflow-overview">
      {promptWorkflow && (
        <WorkflowPromptDialog
          workflowName={promptWorkflow.name}
          busy={startingId === promptWorkflow.id}
          onSubmit={(prompt) => void launchWorkflow(promptWorkflow, { prompt })}
          onCancel={() => setPromptWorkflow(null)}
        />
      )}

      {listError && (
        <p className={fieldErrorClass} role="alert">
          Workflows could not be loaded — {listError}
        </p>
      )}

      {!listError && visibleWorkflows.length === 0 && (
        <p className={fieldHintClass}>No workflows defined yet. Create one in the Design tab.</p>
      )}

      {catalogueError && (
        <p className={fieldErrorClass} role="alert">
          {catalogueError}
        </p>
      )}

      <div className={styles.workflowCardGrid}>
        {visibleWorkflows.map((workflow) => (
          <Panel as="article" className={styles.workflowCard} key={workflow.id}>
            <header className={styles.workflowCardHeader}>
              <div>
                <Eyebrow>{workflow.id}</Eyebrow>
                <h2>{workflow.name}</h2>
              </div>
              <StatusChip label={workflow.status} kind={runStatusKind(workflow.status ?? '')} />
            </header>

            <dl className={styles.workflowCardMeta}>
              <div>
                <dt>Project</dt>
                <dd>{workflow.projectName}</dd>
              </div>
              <div>
                <dt>Triggers</dt>
                <dd>{triggerSummary(workflow)}</dd>
              </div>
              <div>
                <dt>Agents</dt>
                <dd>{agentSummary(workflow.agentIds, agentIndex, catalogueError !== null)}</dd>
              </div>
              <div>
                <dt>Subagents</dt>
                <dd>
                  {workflow.subagentNames.length > 0 ? workflow.subagentNames.join(', ') : 'None'}
                </dd>
              </div>
              <div>
                <dt>Skills</dt>
                <dd>{workflow.skillNames.length > 0 ? workflow.skillNames.join(', ') : 'None'}</dd>
              </div>
              <div>
                <dt>MCP tools</dt>
                <dd>
                  {workflow.mcpTools.length > 0
                    ? workflow.mcpTools.map((t) => t.name).join(', ')
                    : 'None'}
                </dd>
              </div>
              <div>
                <dt>Last execution</dt>
                <dd>
                  {workflow.lastExecutionStatus ?? 'Never run'}
                  {workflow.lastExecutionAt
                    ? ` — ${new Date(workflow.lastExecutionAt).toLocaleString()}`
                    : ''}
                </dd>
              </div>
              <div>
                <dt>OPA enforcement</dt>
                <dd>
                  <StatusChip
                    label={opaEnabled ? 'Enabled' : 'Disabled'}
                    kind={opaEnabled ? 'ok' : 'waiting'}
                  />{' '}
                  {decisionSummaries[workflow.id]?.lastResult
                    ? `Last decision: ${decisionSummaries[workflow.id]?.lastResult} (${decisionSummaries[workflow.id]?.lastDecisionId ?? 'n/a'})`
                    : 'No decisions recorded yet'}
                </dd>
              </div>
              <div>
                <dt>Decisions</dt>
                <dd>
                  Allowed {decisionSummaries[workflow.id]?.allowed ?? 0} · Denied{' '}
                  {decisionSummaries[workflow.id]?.denied ?? 0} · Requires approval{' '}
                  {decisionSummaries[workflow.id]?.requiresApproval ?? 0}
                </dd>
              </div>
            </dl>

            <div className={styles.workflowCardActions}>
              <Button
                variant="primary"
                onClick={() => void handleStart(workflow)}
                disabled={startingId === workflow.id}
              >
                {startingId === workflow.id ? '⟳ Starting…' : '▶ Start'}
              </Button>
              <Button
                onClick={() => void handleExportDecisionLogs(workflow)}
                disabled={exportingId === workflow.id}
              >
                {exportingId === workflow.id ? '⟳ Exporting…' : '⬇ Export decision logs'}
              </Button>
              {onViewExecutionTab && (
                <Button variant="link" onClick={() => onViewExecutionTab(workflow)}>
                  Open Workflow Execution <span>→</span>
                </Button>
              )}
              {feedback[workflow.id] && (
                <span className={styles.workflowCardFeedback}>{feedback[workflow.id]}</span>
              )}
            </div>
          </Panel>
        ))}
      </div>
    </section>
  );
}
