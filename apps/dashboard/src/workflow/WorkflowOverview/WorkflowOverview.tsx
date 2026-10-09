import './WorkflowOverview.scss';
import { useState } from 'react';
import type { Project, AgentDefinition } from '../../types';
import { fetchAgentDefinitions } from '../../api';
import {
  getOpaConfig,
  getWorkflowDecisionLogs,
  listWorkflowGroups,
  listWorkflows,
  startWorkflow,
} from '../workflowApi';
import type { PolicyDecisionAuditEntry, WorkflowDefinition, WorkflowGroup } from '../workflowTypes';
import { workflowsForProject } from '../workflowVisibility';
import { StatusChip } from '../../components/StatusChip/StatusChip';
import { runStatusKind } from '../runStatus';
import { WorkflowPromptDialog } from '../WorkflowPromptDialog/WorkflowPromptDialog';

interface Props {
  project: Project | null;
  onWorkflowStarted?: (workflow: WorkflowDefinition, runId: string) => void;
  onViewExecutionTab?: (workflow: WorkflowDefinition) => void;
}

interface DecisionSummary {
  lastResult?: string;
  lastDecisionId?: string;
  allowed: number;
  denied: number;
  requiresApproval: number;
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
  const known: AgentDefinition[] = [];
  const unknown: string[] = [];
  for (const id of agentIds) {
    const agent = index.get(id);
    if (agent) known.push(agent);
    else unknown.push(id);
  }
  known.sort((a, b) => a.sequenceOrder - b.sequenceOrder);
  const parts = [
    ...known.map((agent) => agent.name),
    ...unknown.map((id) => `${id} (unrecognised)`),
  ];
  return parts.join(', ');
}

function summarize(entries: PolicyDecisionAuditEntry[]): DecisionSummary {
  const summary: DecisionSummary = { allowed: 0, denied: 0, requiresApproval: 0 };
  for (const entry of entries) {
    if (entry.requiredApproval) summary.requiresApproval += 1;
    else if (entry.opaDecisionResult === 'ALLOWED') summary.allowed += 1;
    else if (entry.opaDecisionResult === 'DENIED') summary.denied += 1;
  }
  const last = entries[entries.length - 1];
  if (last) {
    summary.lastResult = last.opaDecisionResult;
    summary.lastDecisionId = last.opaDecisionId;
  }
  return summary;
}

function downloadDecisionLogs(workflowId: string, entries: PolicyDecisionAuditEntry[]) {
  const blob = new Blob([JSON.stringify(entries, null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `${workflowId}-decision-logs.json`;
  link.click();
  URL.revokeObjectURL(url);
}

export function WorkflowOverview({ project, onWorkflowStarted, onViewExecutionTab }: Props) {
  const [workflows, setWorkflows] = useState<WorkflowDefinition[]>([]);
  const [groups, setGroups] = useState<WorkflowGroup[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [startingId, setStartingId] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<Record<string, string>>({});
  const [opaEnabled, setOpaEnabled] = useState(false);
  const [decisionSummaries, setDecisionSummaries] = useState<Record<string, DecisionSummary>>({});
  const [exportingId, setExportingId] = useState<string | null>(null);
  const [promptWorkflow, setPromptWorkflow] = useState<WorkflowDefinition | null>(null);
  const [agentCatalogue, setAgentCatalogue] = useState<AgentDefinition[]>([]);
  const [catalogueError, setCatalogueError] = useState<string | null>(null);
  const [listError, setListError] = useState<string | null>(null);

  if (!loaded) {
    void listWorkflows()
      .then(async (workflowList) => {
        setWorkflows(workflowList);
        void listWorkflowGroups().then(setGroups);
        setLoaded(true);
        getOpaConfig()
          .then((config) => setOpaEnabled(config.enabled))
          .catch(() => setOpaEnabled(false));
        const summaries = await Promise.all(
          workflowList.map(async (workflow) => {
            const entries = await getWorkflowDecisionLogs(workflow.id).catch(() => []);
            return [workflow.id, summarize(entries)] as const;
          }),
        );
        setDecisionSummaries(Object.fromEntries(summaries));
      })
      .catch((error: unknown) => {
        setWorkflows([]);
        setListError(error instanceof Error ? error.message : 'Failed to load workflows');
        setLoaded(true);
      });
    fetchAgentDefinitions()
      .then(setAgentCatalogue)
      .catch(() => {
        setAgentCatalogue([]);
        setCatalogueError('Agent names could not be loaded — showing agent ids.');
      });
    return <div className="auth-loading">Loading workflows...</div>;
  }

  async function handleExportDecisionLogs(workflow: WorkflowDefinition) {
    setExportingId(workflow.id);
    try {
      const entries = await getWorkflowDecisionLogs(workflow.id);
      downloadDecisionLogs(workflow.id, entries);
    } catch (error) {
      console.warn('Failed to export decision logs', error);
    } finally {
      setExportingId(null);
    }
  }

  const visibleWorkflows = workflowsForProject(workflows, groups, project?.name);
  const agentIndex = new Map(agentCatalogue.map((agent) => [agent.id, agent]));

  async function handleStart(workflow: WorkflowDefinition) {
    if (workflow.promptRequired) {
      setPromptWorkflow(workflow);
      return;
    }
    await launchWorkflow(workflow, {});
  }

  async function launchWorkflow(workflow: WorkflowDefinition, input: { prompt?: string }) {
    setStartingId(workflow.id);
    setFeedback((prev) => ({ ...prev, [workflow.id]: '' }));
    try {
      const result = await startWorkflow(workflow.id, input);
      setPromptWorkflow(null);
      setFeedback((prev) => ({
        ...prev,
        [workflow.id]:
          result.status === 'BLOCKED'
            ? result.message
            : `Started — execution ${result.executionId} (${result.status})`,
      }));
      if (result.executionId) {
        setWorkflows((prev) =>
          prev.map((w) =>
            w.id === workflow.id
              ? { ...w, lastExecutionStatus: result.status, lastExecutionAt: result.startedAt }
              : w,
          ),
        );
        onWorkflowStarted?.(workflow, result.executionId);
      }
    } catch (error) {
      const message =
        error instanceof Error && error.message ? error.message : 'Failed to start workflow';
      setFeedback((prev) => ({ ...prev, [workflow.id]: message }));
      console.warn('Failed to start workflow', error);
    } finally {
      setStartingId(null);
    }
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
        <p className="settings-error" role="alert">
          Workflows could not be loaded — {listError}
        </p>
      )}

      {!listError && visibleWorkflows.length === 0 && (
        <p className="settings-desc">No workflows defined yet. Create one in the Design tab.</p>
      )}

      {catalogueError && (
        <p className="settings-error" role="alert">
          {catalogueError}
        </p>
      )}

      <div className="workflow-card-grid">
        {visibleWorkflows.map((workflow) => (
          <article className="panel workflow-card" key={workflow.id}>
            <header className="workflow-card-header">
              <div>
                <span className="eyebrow">{workflow.id}</span>
                <h2>{workflow.name}</h2>
              </div>
              <StatusChip label={workflow.status} kind={runStatusKind(workflow.status ?? '')} />
            </header>

            <dl className="workflow-card-meta">
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
                    ? `Last decision: ${decisionSummaries[workflow.id].lastResult} (${decisionSummaries[workflow.id].lastDecisionId ?? 'n/a'})`
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

            <div className="workflow-card-actions">
              <button
                className="button button--start"
                onClick={() => void handleStart(workflow)}
                disabled={startingId === workflow.id}
              >
                {startingId === workflow.id ? '⟳ Starting…' : '▶ Start'}
              </button>
              <button
                className="button"
                onClick={() => void handleExportDecisionLogs(workflow)}
                disabled={exportingId === workflow.id}
              >
                {exportingId === workflow.id ? '⟳ Exporting…' : '⬇ Export decision logs'}
              </button>
              {onViewExecutionTab && (
                <button className="link-button" onClick={() => onViewExecutionTab(workflow)}>
                  Open Workflow Execution <span>→</span>
                </button>
              )}
              {feedback[workflow.id] && (
                <span className="workflow-card-feedback">{feedback[workflow.id]}</span>
              )}
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}
