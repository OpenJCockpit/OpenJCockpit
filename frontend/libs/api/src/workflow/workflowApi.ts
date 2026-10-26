import { apiRequest, apiRequestVoid } from '../core/http';
import {
  A2AAgentCard,
  A2AService,
  A2AServiceRequest,
  AgentCatalog,
  AgentSpec,
  ApprovalDecisionAuditEntry,
  ApprovalDecisionRequest,
  ApprovalDecisionResult,
  ApprovalGateContext,
  DecisionLogFilter,
  DocumentFolderConfig,
  DocumentFolderConfigRequest,
  HermesConfig,
  HermesConfigRequest,
  JiraConfig,
  JiraConfigRequest,
  OpaConfig,
  OpaConfigRequest,
  PolicyDecisionAuditEntry,
  SkillCatalog,
  SkillSpec,
  SubagentSpec,
  WorkflowDefinition,
  WorkflowExecutionPage,
  WorkflowExportBundle,
  WorkflowGroup,
  WorkflowImportResult,
  WorkflowStartInput,
  WorkflowStartResponse,
} from './workflowTypes';
import type { AgentRun } from '../types/types';

// ── Workflows ────────────────────────────────────────────────────────────

export function listWorkflows(signal?: AbortSignal): Promise<WorkflowDefinition[]> {
  return apiRequest('/api/workflows', { signal, errorMessage: 'Failed to load workflows' });
}

export function getWorkflow(id: string): Promise<WorkflowDefinition> {
  return apiRequest(`/api/workflows/${id}`, {
    errorMessage: 'Failed to load workflow',
  });
}

export function createWorkflow(request: WorkflowDefinition): Promise<WorkflowDefinition> {
  return apiRequest(`/api/workflows`, {
    method: 'POST',
    body: request,
    errorMessage: 'Failed to create workflow',
  });
}

export function updateWorkflow(
  id: string,
  request: WorkflowDefinition,
): Promise<WorkflowDefinition> {
  return apiRequest(`/api/workflows/${id}`, {
    method: 'PUT',
    body: request,
    errorMessage: 'Failed to update workflow',
  });
}

export function deleteWorkflow(id: string): Promise<void> {
  return apiRequestVoid(`/api/workflows/${id}`, {
    method: 'DELETE',
    errorMessage: 'Failed to delete workflow',
  });
}

export function startWorkflow(
  id: string,
  input: WorkflowStartInput = {},
): Promise<WorkflowStartResponse> {
  return apiRequest(`/api/workflows/${id}/start`, {
    method: 'POST',
    body: input,
    errorMessage: 'Failed to start workflow',
  });
}

// ── Workflow groups & export/import ──────────────────────────────────────

export function listWorkflowGroups(signal?: AbortSignal): Promise<WorkflowGroup[]> {
  return apiRequest('/api/workflow-groups', {
    signal,
    errorMessage: 'Failed to load workflow groups',
  });
}

export function createWorkflowGroup(request: WorkflowGroup): Promise<WorkflowGroup> {
  return apiRequest(`/api/workflow-groups`, {
    method: 'POST',
    body: request,
    errorMessage: 'Failed to create workflow group',
  });
}

export function updateWorkflowGroup(id: string, request: WorkflowGroup): Promise<WorkflowGroup> {
  return apiRequest(`/api/workflow-groups/${id}`, {
    method: 'PUT',
    body: request,
    errorMessage: 'Failed to update workflow group',
  });
}

export function deleteWorkflowGroup(id: string): Promise<void> {
  return apiRequestVoid(`/api/workflow-groups/${id}`, {
    method: 'DELETE',
    errorMessage: 'Failed to delete workflow group',
  });
}

export function exportWorkflows(): Promise<WorkflowExportBundle> {
  return apiRequest(`/api/workflows/export`, {
    errorMessage: 'Failed to export workflows',
  });
}

export function importWorkflows(bundle: WorkflowExportBundle): Promise<WorkflowImportResult> {
  return apiRequest(`/api/workflows/import`, {
    method: 'POST',
    body: bundle,
    errorMessage: 'Failed to import workflows',
  });
}

function decisionLogQuery(filter?: DecisionLogFilter): string {
  if (!filter) return '';
  const params = new URLSearchParams();
  if (filter.from) params.set('from', filter.from);
  if (filter.to) params.set('to', filter.to);
  if (filter.agentId) params.set('agentId', filter.agentId);
  if (filter.subagentId) params.set('subagentId', filter.subagentId);
  if (filter.skillId) params.set('skillId', filter.skillId);
  if (filter.mcpToolName) params.set('mcpToolName', filter.mcpToolName);
  if (filter.result) params.set('result', filter.result);
  const query = params.toString();
  return query ? `?${query}` : '';
}

export function getWorkflowDecisionLogs(
  workflowId: string,
  filter?: DecisionLogFilter,
): Promise<PolicyDecisionAuditEntry[]> {
  return apiRequest(`/api/workflows/${workflowId}/decision-logs${decisionLogQuery(filter)}`, {
    errorMessage: 'Failed to load decision logs',
  });
}

export function getWorkflowExecutionDecisionLogs(
  workflowExecutionId: string,
  filter?: DecisionLogFilter,
): Promise<PolicyDecisionAuditEntry[]> {
  return apiRequest(
    `/api/workflow-executions/${workflowExecutionId}/decision-logs${decisionLogQuery(filter)}`,
    {
      errorMessage: 'Failed to load decision logs',
    },
  );
}

// ── OPA configuration ────────────────────────────────────────────────────

export function getOpaConfig(): Promise<OpaConfig> {
  return apiRequest(`/api/opa-config`, {
    errorMessage: 'Failed to load OPA config',
  });
}

export function saveOpaConfig(request: OpaConfigRequest): Promise<OpaConfig> {
  return apiRequest(`/api/opa-config`, {
    method: 'PUT',
    body: request,
    errorMessage: 'Failed to save OPA config',
  });
}

export async function checkOpaHealth(): Promise<boolean> {
  try {
    const body = await apiRequest<{ reachable?: boolean }>('/api/opa-config/health', {
      errorMessage: 'Failed to check OPA health',
    });
    return Boolean(body.reachable);
  } catch (error) {
    console.warn('Failed to check OPA health', error);
    return false;
  }
}

// ── Agent definitions ────────────────────────────────────────────────────

/** Local /agents folder plus the agents of each A2A provider (e.g. LiteLLM). */
export function getAgentCatalog(): Promise<AgentCatalog> {
  return apiRequest(`/api/agent-catalog`, {
    errorMessage: 'Failed to load agent catalog',
  });
}

/** The agent card the A2A provider hosts for one agent, fetched live through the backend. */
export function getProviderAgentCard(provider: string, name: string): Promise<A2AAgentCard> {
  return apiRequest(
    `/api/agent-catalog/providers/${encodeURIComponent(provider)}/agents/${encodeURIComponent(name)}/card`,
    {
      errorMessage: 'Failed to load agent card',
    },
  );
}

export async function listAgentSpecs(): Promise<AgentSpec[]> {
  try {
    return await apiRequest('/api/agent-definitions', {
      errorMessage: 'Failed to load agent definitions',
    });
  } catch (error) {
    console.warn('Falling back to empty agent specs', error);
    return [];
  }
}

export function createAgentSpec(request: AgentSpec): Promise<AgentSpec> {
  return apiRequest(`/api/agent-definitions`, {
    method: 'POST',
    body: request,
    errorMessage: 'Failed to create agent definition',
  });
}

export function updateAgentSpec(name: string, request: AgentSpec): Promise<AgentSpec> {
  return apiRequest(`/api/agent-definitions/${name}`, {
    method: 'PUT',
    body: request,
    errorMessage: 'Failed to update agent definition',
  });
}

export function deleteAgentSpec(name: string): Promise<void> {
  return apiRequestVoid(`/api/agent-definitions/${name}`, {
    method: 'DELETE',
    errorMessage: 'Failed to delete agent definition',
  });
}

export function generateAgentSpec(prompt: string): Promise<AgentSpec> {
  return apiRequest(`/api/agent-definitions/generate-from-prompt`, {
    method: 'POST',
    body: { prompt },
    errorMessage: 'Failed to generate agent definition',
  });
}

// ── Subagent definitions ─────────────────────────────────────────────────

export function listSubagentSpecs(signal?: AbortSignal): Promise<SubagentSpec[]> {
  return apiRequest('/api/subagent-definitions', {
    signal,
    errorMessage: 'Failed to load subagent definitions',
  });
}

export function createSubagentSpec(request: SubagentSpec): Promise<SubagentSpec> {
  return apiRequest(`/api/subagent-definitions`, {
    method: 'POST',
    body: request,
    errorMessage: 'Failed to create subagent definition',
  });
}

export function updateSubagentSpec(name: string, request: SubagentSpec): Promise<SubagentSpec> {
  return apiRequest(`/api/subagent-definitions/${name}`, {
    method: 'PUT',
    body: request,
    errorMessage: 'Failed to update subagent definition',
  });
}

export function deleteSubagentSpec(name: string): Promise<void> {
  return apiRequestVoid(`/api/subagent-definitions/${name}`, {
    method: 'DELETE',
    errorMessage: 'Failed to delete subagent definition',
  });
}

export function generateSubagentSpec(prompt: string): Promise<SubagentSpec> {
  return apiRequest(`/api/subagent-definitions/generate-from-prompt`, {
    method: 'POST',
    body: { prompt },
    errorMessage: 'Failed to generate subagent definition',
  });
}

// ── Skill definitions ────────────────────────────────────────────────────

export function listSkillSpecs(signal?: AbortSignal): Promise<SkillSpec[]> {
  return apiRequest('/api/skill-definitions', {
    signal,
    errorMessage: 'Failed to load skill definitions',
  });
}

export function createSkillSpec(request: SkillSpec): Promise<SkillSpec> {
  return apiRequest(`/api/skill-definitions`, {
    method: 'POST',
    body: request,
    errorMessage: 'Failed to create skill definition',
  });
}

export function updateSkillSpec(name: string, request: SkillSpec): Promise<SkillSpec> {
  return apiRequest(`/api/skill-definitions/${name}`, {
    method: 'PUT',
    body: request,
    errorMessage: 'Failed to update skill definition',
  });
}

export function deleteSkillSpec(name: string): Promise<void> {
  return apiRequestVoid(`/api/skill-definitions/${name}`, {
    method: 'DELETE',
    errorMessage: 'Failed to delete skill definition',
  });
}

export function generateSkillSpec(prompt: string): Promise<SkillSpec> {
  return apiRequest(`/api/skill-definitions/generate-from-prompt`, {
    method: 'POST',
    body: { prompt },
    errorMessage: 'Failed to generate skill definition',
  });
}

// ── A2A services ─────────────────────────────────────────────────────────

export function listA2AServices(): Promise<A2AService[]> {
  return apiRequest(`/api/a2a-services`, {
    errorMessage: 'Failed to load A2A services',
  });
}

export function createA2AService(request: A2AServiceRequest): Promise<A2AService> {
  return apiRequest(`/api/a2a-services`, {
    method: 'POST',
    body: request,
    errorMessage: 'Failed to add A2A service',
  });
}

export function updateA2AService(id: string, request: A2AServiceRequest): Promise<A2AService> {
  return apiRequest(`/api/a2a-services/${encodeURIComponent(id)}`, {
    method: 'PUT',
    body: request,
    errorMessage: 'Failed to save A2A service',
  });
}

export function deleteA2AService(id: string): Promise<void> {
  return apiRequestVoid(`/api/a2a-services/${encodeURIComponent(id)}`, {
    method: 'DELETE',
    errorMessage: 'Failed to remove A2A service',
  });
}

// ── Skill catalog (local + marketplace) ──────────────────────────────────
// Intentionally throw-on-failure, unlike listSkillSpecs above: a real failure
// (e.g. 401) must surface as a real error, never as silently-rendered mock data.

export function getSkillCatalog(): Promise<SkillCatalog> {
  return apiRequest(`/api/skill-catalog`, {
    errorMessage: 'Failed to load skill catalog',
  });
}

// ── Project integration config ───────────────────────────────────────────

export function getHermesConfig(projectId: string): Promise<HermesConfig> {
  return apiRequest(`/api/projects/${projectId}/hermes-config`, {
    errorMessage: 'Failed to load Hermes config',
  });
}

export function saveHermesConfig(
  projectId: string,
  request: HermesConfigRequest,
): Promise<HermesConfig> {
  return apiRequest(`/api/projects/${projectId}/hermes-config`, {
    method: 'POST',
    body: request,
    errorMessage: 'Failed to save Hermes config',
  });
}

export function deleteHermesConfig(projectId: string): Promise<void> {
  return apiRequestVoid(`/api/projects/${projectId}/hermes-config`, {
    method: 'DELETE',
    errorMessage: 'Failed to delete Hermes config',
  });
}

export function getJiraConfig(projectId: string): Promise<JiraConfig> {
  return apiRequest(`/api/projects/${projectId}/jira-config`, {
    errorMessage: 'Failed to load Jira config',
  });
}

export function saveJiraConfig(projectId: string, request: JiraConfigRequest): Promise<JiraConfig> {
  return apiRequest(`/api/projects/${projectId}/jira-config`, {
    method: 'POST',
    body: request,
    errorMessage: 'Failed to save Jira config',
  });
}

export function deleteJiraConfig(projectId: string): Promise<void> {
  return apiRequestVoid(`/api/projects/${projectId}/jira-config`, {
    method: 'DELETE',
    errorMessage: 'Failed to delete Jira config',
  });
}

export function getDocumentFolderConfig(projectId: string): Promise<DocumentFolderConfig> {
  return apiRequest(`/api/projects/${projectId}/document-folder-config`, {
    errorMessage: 'Failed to load document folder config',
  });
}

export function saveDocumentFolderConfig(
  projectId: string,
  request: DocumentFolderConfigRequest,
): Promise<DocumentFolderConfig> {
  return apiRequest(`/api/projects/${projectId}/document-folder-config`, {
    method: 'POST',
    body: request,
    errorMessage: 'Failed to save document folder config',
  });
}

export function deleteDocumentFolderConfig(projectId: string): Promise<void> {
  return apiRequestVoid(`/api/projects/${projectId}/document-folder-config`, {
    method: 'DELETE',
    errorMessage: 'Failed to delete document folder config',
  });
}

// ── Human approval gate ──────────────────────────────────────────────────
// Throw-on-failure, deliberately never the listSkillSpecs swallow-to-mock idiom:
// a decision, its context, or its audit trail must never silently degrade into
// fabricated data.

export function getApprovalGateContext(runId: string): Promise<ApprovalGateContext> {
  return apiRequest(`/api/agent-runs/${runId}/approval-gate`, {
    errorMessage: 'Failed to load the approval gate',
  });
}

export function submitApprovalDecision(
  runId: string,
  request: ApprovalDecisionRequest,
): Promise<ApprovalDecisionResult> {
  return apiRequest(`/api/agent-runs/${runId}/approval-gate/decision`, {
    method: 'POST',
    body: request,
    errorMessage: 'Failed to submit the approval decision',
  });
}

export function listApprovalDecisions(runId: string): Promise<ApprovalDecisionAuditEntry[]> {
  return apiRequest(`/api/agent-runs/${runId}/approval-decisions`, {
    errorMessage: 'Failed to load approval decisions',
  });
}

// ── Workflow execution history ───────────────────────────────────────────
// Throw-on-failure, deliberately never the listWorkflows swallow-to-[] idiom:
// a downstream failure must never silently look like "zero executions".

export function listWorkflowExecutions(
  workflowId: string,
  params?: { limit?: number; offset?: number },
): Promise<WorkflowExecutionPage> {
  const query = new URLSearchParams();
  if (params?.limit !== undefined) query.set('limit', String(params.limit));
  if (params?.offset !== undefined) query.set('offset', String(params.offset));
  const queryString = query.toString();
  return apiRequest(
    `/api/workflows/${workflowId}/executions${queryString ? `?${queryString}` : ''}`,
    {
      errorMessage: 'Failed to load workflow executions',
    },
  );
}

export function getWorkflowExecution(workflowId: string, runId: string): Promise<AgentRun> {
  return apiRequest(`/api/workflows/${workflowId}/executions/${runId}`, {
    errorMessage: 'Failed to load workflow execution',
  });
}
