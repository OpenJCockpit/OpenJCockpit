import { useEffect, useState } from 'react';
import {
  getOpaConfig,
  getWorkflowDecisionLogs,
  listWorkflowGroups,
  listWorkflows,
  startWorkflow,
  workflowsForProject,
} from '@openjcockpit/api';
import type {
  AgentDefinition,
  PolicyDecisionAuditEntry,
  Project,
  WorkflowDefinition,
  WorkflowGroup,
} from '@openjcockpit/api';
import { fetchLinkableAgents } from '../linkableAgents';

interface DecisionSummary {
  lastResult?: string;
  lastDecisionId?: string;
  allowed: number;
  denied: number;
  requiresApproval: number;
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

/** Workflow list, decision summaries, agent catalogue and the start / export actions for the overview. */
export function useWorkflowOverview(
  project: Project | null,
  onWorkflowStarted?: (workflow: WorkflowDefinition, runId: string) => void,
) {
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

  useEffect(() => {
    let active = true;
    void listWorkflows()
      .then(async (workflowList) => {
        if (!active) return;
        setWorkflows(workflowList);
        void listWorkflowGroups().then((list) => active && setGroups(list));
        setLoaded(true);
        getOpaConfig()
          .then((config) => active && setOpaEnabled(config.enabled))
          .catch(() => active && setOpaEnabled(false));
        const summaries = await Promise.all(
          workflowList.map(async (workflow) => {
            const entries = await getWorkflowDecisionLogs(workflow.id).catch(() => []);
            return [workflow.id, summarize(entries)] as const;
          }),
        );
        if (active) setDecisionSummaries(Object.fromEntries(summaries));
      })
      .catch((error: unknown) => {
        if (!active) return;
        setWorkflows([]);
        setListError(error instanceof Error ? error.message : 'Failed to load workflows');
        setLoaded(true);
      });
    fetchLinkableAgents()
      .then((agents) => active && setAgentCatalogue(agents))
      .catch(() => {
        if (!active) return;
        setAgentCatalogue([]);
        setCatalogueError('Agent names could not be loaded — showing agent ids.');
      });
    return () => {
      active = false;
    };
  }, []);

  async function handleExportDecisionLogs(workflow: WorkflowDefinition) {
    setExportingId(workflow.id);
    setFeedback((prev) => ({ ...prev, [workflow.id]: '' }));
    try {
      const entries = await getWorkflowDecisionLogs(workflow.id);
      downloadDecisionLogs(workflow.id, entries);
    } catch (error) {
      console.warn('Failed to export decision logs', error);
      setFeedback((prev) => ({
        ...prev,
        [workflow.id]:
          error instanceof Error && error.message
            ? error.message
            : 'Failed to export decision logs',
      }));
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

  return {
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
  };
}
