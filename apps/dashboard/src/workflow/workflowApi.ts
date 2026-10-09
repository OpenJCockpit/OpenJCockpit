import { API_BASE_URL, authHeaders } from '../api';
import {
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
import type { AgentRun } from '../types';
import { mockSkillSpecs, mockSubagentSpecs } from './mockWorkflows';

const JSON_HEADERS = { 'Content-Type': 'application/json' };

// Parses the server's ApiErrorResponse.message from a failed response body, falling
// back to a generic "<fallback>: <status>" message when the body isn't JSON or has
// no message (e.g. a 401 from an infrastructure layer with no JSON body at all).
export async function errorMessageFrom(response: Response, fallback: string): Promise<string> {
  try {
    const body = await response.json();
    if (body && typeof body.message === 'string' && body.message.trim()) return body.message;
  } catch {
    // response body wasn't JSON (or was empty) — fall back below
  }
  return `${fallback}: ${response.status}`;
}

// ── Workflows ────────────────────────────────────────────────────────────

export async function listWorkflows(): Promise<WorkflowDefinition[]> {
  const response = await fetch(`${API_BASE_URL}/api/workflows`, { headers: authHeaders() });
  if (!response.ok) throw new Error(await errorMessageFrom(response, 'Failed to load workflows'));
  return response.json();
}

export async function getWorkflow(id: string): Promise<WorkflowDefinition> {
  const response = await fetch(`${API_BASE_URL}/api/workflows/${id}`, { headers: authHeaders() });
  if (!response.ok) throw new Error(`Failed to load workflow: ${response.status}`);
  return response.json();
}

export async function createWorkflow(request: WorkflowDefinition): Promise<WorkflowDefinition> {
  const response = await fetch(`${API_BASE_URL}/api/workflows`, {
    method: 'POST',
    headers: { ...JSON_HEADERS, ...authHeaders() },
    body: JSON.stringify(request),
  });
  // Surfaces the server's actual ApiErrorResponse.message (e.g. the approval-gate
  // placement validation naming the offending stage) instead of a generic status-only
  // message — required for the gate's validation error to reach the user (AC-03).
  if (!response.ok) throw new Error(await errorMessageFrom(response, 'Failed to create workflow'));
  return response.json();
}

export async function updateWorkflow(
  id: string,
  request: WorkflowDefinition,
): Promise<WorkflowDefinition> {
  const response = await fetch(`${API_BASE_URL}/api/workflows/${id}`, {
    method: 'PUT',
    headers: { ...JSON_HEADERS, ...authHeaders() },
    body: JSON.stringify(request),
  });
  if (!response.ok) throw new Error(await errorMessageFrom(response, 'Failed to update workflow'));
  return response.json();
}

export async function deleteWorkflow(id: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/api/workflows/${id}`, {
    method: 'DELETE',
    headers: authHeaders(),
  });
  if (!response.ok) throw new Error(await errorMessageFrom(response, 'Failed to delete workflow'));
}

export async function startWorkflow(
  id: string,
  input: WorkflowStartInput = {},
): Promise<WorkflowStartResponse> {
  const response = await fetch(`${API_BASE_URL}/api/workflows/${id}/start`, {
    method: 'POST',
    headers: { ...JSON_HEADERS, ...authHeaders() },
    body: JSON.stringify(input),
  });
  if (!response.ok) throw new Error(await errorMessageFrom(response, 'Failed to start workflow'));
  return response.json();
}

// ── Workflow groups & export/import ──────────────────────────────────────

export async function listWorkflowGroups(): Promise<WorkflowGroup[]> {
  try {
    const response = await fetch(`${API_BASE_URL}/api/workflow-groups`, { headers: authHeaders() });
    if (!response.ok) {
      console.warn('Falling back to empty workflow groups: status', response.status);
      return [];
    }
    return await response.json();
  } catch (error) {
    console.warn('Falling back to empty workflow groups', error);
    return [];
  }
}

export async function createWorkflowGroup(request: WorkflowGroup): Promise<WorkflowGroup> {
  const response = await fetch(`${API_BASE_URL}/api/workflow-groups`, {
    method: 'POST',
    headers: { ...JSON_HEADERS, ...authHeaders() },
    body: JSON.stringify(request),
  });
  if (!response.ok) throw new Error(`Failed to create workflow group: ${response.status}`);
  return response.json();
}

export async function updateWorkflowGroup(
  id: string,
  request: WorkflowGroup,
): Promise<WorkflowGroup> {
  const response = await fetch(`${API_BASE_URL}/api/workflow-groups/${id}`, {
    method: 'PUT',
    headers: { ...JSON_HEADERS, ...authHeaders() },
    body: JSON.stringify(request),
  });
  if (!response.ok) throw new Error(`Failed to update workflow group: ${response.status}`);
  return response.json();
}

export async function deleteWorkflowGroup(id: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/api/workflow-groups/${id}`, {
    method: 'DELETE',
    headers: authHeaders(),
  });
  if (!response.ok) throw new Error(`Failed to delete workflow group: ${response.status}`);
}

export async function exportWorkflows(): Promise<WorkflowExportBundle> {
  const response = await fetch(`${API_BASE_URL}/api/workflows/export`, { headers: authHeaders() });
  if (!response.ok) throw new Error(`Failed to export workflows: ${response.status}`);
  return response.json();
}

export async function importWorkflows(bundle: WorkflowExportBundle): Promise<WorkflowImportResult> {
  const response = await fetch(`${API_BASE_URL}/api/workflows/import`, {
    method: 'POST',
    headers: { ...JSON_HEADERS, ...authHeaders() },
    body: JSON.stringify(bundle),
  });
  if (!response.ok) throw new Error(`Failed to import workflows: ${response.status}`);
  return response.json();
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

export async function getWorkflowDecisionLogs(
  workflowId: string,
  filter?: DecisionLogFilter,
): Promise<PolicyDecisionAuditEntry[]> {
  const response = await fetch(
    `${API_BASE_URL}/api/workflows/${workflowId}/decision-logs${decisionLogQuery(filter)}`,
    {
      headers: authHeaders(),
    },
  );
  if (!response.ok) throw new Error(`Failed to load decision logs: ${response.status}`);
  return response.json();
}

export async function getWorkflowExecutionDecisionLogs(
  workflowExecutionId: string,
  filter?: DecisionLogFilter,
): Promise<PolicyDecisionAuditEntry[]> {
  const response = await fetch(
    `${API_BASE_URL}/api/workflow-executions/${workflowExecutionId}/decision-logs${decisionLogQuery(filter)}`,
    {
      headers: authHeaders(),
    },
  );
  if (!response.ok) throw new Error(`Failed to load decision logs: ${response.status}`);
  return response.json();
}

// ── OPA configuration ────────────────────────────────────────────────────

export async function getOpaConfig(): Promise<OpaConfig> {
  const response = await fetch(`${API_BASE_URL}/api/opa-config`, { headers: authHeaders() });
  if (!response.ok) throw new Error(`Failed to load OPA config: ${response.status}`);
  return response.json();
}

export async function saveOpaConfig(request: OpaConfigRequest): Promise<OpaConfig> {
  const response = await fetch(`${API_BASE_URL}/api/opa-config`, {
    method: 'PUT',
    headers: { ...JSON_HEADERS, ...authHeaders() },
    body: JSON.stringify(request),
  });
  if (!response.ok) throw new Error(`Failed to save OPA config: ${response.status}`);
  return response.json();
}

export async function checkOpaHealth(): Promise<boolean> {
  try {
    const response = await fetch(`${API_BASE_URL}/api/opa-config/health`, {
      headers: authHeaders(),
    });
    if (!response.ok) return false;
    const body = await response.json();
    return Boolean(body.reachable);
  } catch (error) {
    console.warn('Failed to check OPA health', error);
    return false;
  }
}

// ── Agent definitions ────────────────────────────────────────────────────

export async function listAgentSpecs(): Promise<AgentSpec[]> {
  try {
    const response = await fetch(`${API_BASE_URL}/api/agent-definitions`, {
      headers: authHeaders(),
    });
    if (!response.ok) {
      console.warn('Falling back to empty agent specs: status', response.status);
      return [];
    }
    return await response.json();
  } catch (error) {
    console.warn('Falling back to empty agent specs', error);
    return [];
  }
}

export async function createAgentSpec(request: AgentSpec): Promise<AgentSpec> {
  const response = await fetch(`${API_BASE_URL}/api/agent-definitions`, {
    method: 'POST',
    headers: { ...JSON_HEADERS, ...authHeaders() },
    body: JSON.stringify(request),
  });
  if (!response.ok) throw new Error(`Failed to create agent definition: ${response.status}`);
  return response.json();
}

export async function updateAgentSpec(name: string, request: AgentSpec): Promise<AgentSpec> {
  const response = await fetch(`${API_BASE_URL}/api/agent-definitions/${name}`, {
    method: 'PUT',
    headers: { ...JSON_HEADERS, ...authHeaders() },
    body: JSON.stringify(request),
  });
  if (!response.ok) throw new Error(`Failed to update agent definition: ${response.status}`);
  return response.json();
}

export async function deleteAgentSpec(name: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/api/agent-definitions/${name}`, {
    method: 'DELETE',
    headers: authHeaders(),
  });
  if (!response.ok) throw new Error(`Failed to delete agent definition: ${response.status}`);
}

export async function generateAgentSpec(prompt: string): Promise<AgentSpec> {
  const response = await fetch(`${API_BASE_URL}/api/agent-definitions/generate-from-prompt`, {
    method: 'POST',
    headers: { ...JSON_HEADERS, ...authHeaders() },
    body: JSON.stringify({ prompt }),
  });
  if (!response.ok) throw new Error(`Failed to generate agent definition: ${response.status}`);
  return response.json();
}

// ── Subagent definitions ─────────────────────────────────────────────────

export async function listSubagentSpecs(): Promise<SubagentSpec[]> {
  try {
    const response = await fetch(`${API_BASE_URL}/api/subagent-definitions`, {
      headers: authHeaders(),
    });
    if (!response.ok) return mockSubagentSpecs;
    return await response.json();
  } catch (error) {
    console.warn('Falling back to mock subagent definitions', error);
    return mockSubagentSpecs;
  }
}

export async function createSubagentSpec(request: SubagentSpec): Promise<SubagentSpec> {
  const response = await fetch(`${API_BASE_URL}/api/subagent-definitions`, {
    method: 'POST',
    headers: { ...JSON_HEADERS, ...authHeaders() },
    body: JSON.stringify(request),
  });
  if (!response.ok) throw new Error(`Failed to create subagent definition: ${response.status}`);
  return response.json();
}

export async function updateSubagentSpec(
  name: string,
  request: SubagentSpec,
): Promise<SubagentSpec> {
  const response = await fetch(`${API_BASE_URL}/api/subagent-definitions/${name}`, {
    method: 'PUT',
    headers: { ...JSON_HEADERS, ...authHeaders() },
    body: JSON.stringify(request),
  });
  if (!response.ok) throw new Error(`Failed to update subagent definition: ${response.status}`);
  return response.json();
}

export async function deleteSubagentSpec(name: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/api/subagent-definitions/${name}`, {
    method: 'DELETE',
    headers: authHeaders(),
  });
  if (!response.ok) throw new Error(`Failed to delete subagent definition: ${response.status}`);
}

export async function generateSubagentSpec(prompt: string): Promise<SubagentSpec> {
  const response = await fetch(`${API_BASE_URL}/api/subagent-definitions/generate-from-prompt`, {
    method: 'POST',
    headers: { ...JSON_HEADERS, ...authHeaders() },
    body: JSON.stringify({ prompt }),
  });
  if (!response.ok) throw new Error(`Failed to generate subagent definition: ${response.status}`);
  return response.json();
}

// ── Skill definitions ────────────────────────────────────────────────────

export async function listSkillSpecs(): Promise<SkillSpec[]> {
  try {
    const response = await fetch(`${API_BASE_URL}/api/skill-definitions`, {
      headers: authHeaders(),
    });
    if (!response.ok) return mockSkillSpecs;
    return await response.json();
  } catch (error) {
    console.warn('Falling back to mock skill definitions', error);
    return mockSkillSpecs;
  }
}

export async function createSkillSpec(request: SkillSpec): Promise<SkillSpec> {
  const response = await fetch(`${API_BASE_URL}/api/skill-definitions`, {
    method: 'POST',
    headers: { ...JSON_HEADERS, ...authHeaders() },
    body: JSON.stringify(request),
  });
  if (!response.ok) throw new Error(`Failed to create skill definition: ${response.status}`);
  return response.json();
}

export async function updateSkillSpec(name: string, request: SkillSpec): Promise<SkillSpec> {
  const response = await fetch(`${API_BASE_URL}/api/skill-definitions/${name}`, {
    method: 'PUT',
    headers: { ...JSON_HEADERS, ...authHeaders() },
    body: JSON.stringify(request),
  });
  if (!response.ok) throw new Error(`Failed to update skill definition: ${response.status}`);
  return response.json();
}

export async function deleteSkillSpec(name: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/api/skill-definitions/${name}`, {
    method: 'DELETE',
    headers: authHeaders(),
  });
  if (!response.ok) throw new Error(`Failed to delete skill definition: ${response.status}`);
}

export async function generateSkillSpec(prompt: string): Promise<SkillSpec> {
  const response = await fetch(`${API_BASE_URL}/api/skill-definitions/generate-from-prompt`, {
    method: 'POST',
    headers: { ...JSON_HEADERS, ...authHeaders() },
    body: JSON.stringify({ prompt }),
  });
  if (!response.ok) throw new Error(`Failed to generate skill definition: ${response.status}`);
  return response.json();
}

// ── Skill catalog (local + marketplace) ──────────────────────────────────
// Intentionally throw-on-failure, unlike listSkillSpecs above: a real failure
// (e.g. 401) must surface as a real error, never as silently-rendered mock data.

export async function getSkillCatalog(): Promise<SkillCatalog> {
  const response = await fetch(`${API_BASE_URL}/api/skill-catalog`, { headers: authHeaders() });
  if (!response.ok) throw new Error(`Failed to load skill catalog: ${response.status}`);
  return response.json();
}

// ── Project integration config ───────────────────────────────────────────

export async function getHermesConfig(projectId: string): Promise<HermesConfig> {
  const response = await fetch(`${API_BASE_URL}/api/projects/${projectId}/hermes-config`, {
    headers: authHeaders(),
  });
  if (!response.ok) throw new Error(`Failed to load Hermes config: ${response.status}`);
  return response.json();
}

export async function saveHermesConfig(
  projectId: string,
  request: HermesConfigRequest,
): Promise<HermesConfig> {
  const response = await fetch(`${API_BASE_URL}/api/projects/${projectId}/hermes-config`, {
    method: 'POST',
    headers: { ...JSON_HEADERS, ...authHeaders() },
    body: JSON.stringify(request),
  });
  if (!response.ok) throw new Error(`Failed to save Hermes config: ${response.status}`);
  return response.json();
}

export async function deleteHermesConfig(projectId: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/api/projects/${projectId}/hermes-config`, {
    method: 'DELETE',
    headers: authHeaders(),
  });
  if (!response.ok) throw new Error(`Failed to delete Hermes config: ${response.status}`);
}

export async function getJiraConfig(projectId: string): Promise<JiraConfig> {
  const response = await fetch(`${API_BASE_URL}/api/projects/${projectId}/jira-config`, {
    headers: authHeaders(),
  });
  if (!response.ok) throw new Error(`Failed to load Jira config: ${response.status}`);
  return response.json();
}

export async function saveJiraConfig(
  projectId: string,
  request: JiraConfigRequest,
): Promise<JiraConfig> {
  const response = await fetch(`${API_BASE_URL}/api/projects/${projectId}/jira-config`, {
    method: 'POST',
    headers: { ...JSON_HEADERS, ...authHeaders() },
    body: JSON.stringify(request),
  });
  if (!response.ok) throw new Error(`Failed to save Jira config: ${response.status}`);
  return response.json();
}

export async function deleteJiraConfig(projectId: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/api/projects/${projectId}/jira-config`, {
    method: 'DELETE',
    headers: authHeaders(),
  });
  if (!response.ok) throw new Error(`Failed to delete Jira config: ${response.status}`);
}

export async function getDocumentFolderConfig(projectId: string): Promise<DocumentFolderConfig> {
  const response = await fetch(`${API_BASE_URL}/api/projects/${projectId}/document-folder-config`, {
    headers: authHeaders(),
  });
  if (!response.ok) throw new Error(`Failed to load document folder config: ${response.status}`);
  return response.json();
}

export async function saveDocumentFolderConfig(
  projectId: string,
  request: DocumentFolderConfigRequest,
): Promise<DocumentFolderConfig> {
  const response = await fetch(`${API_BASE_URL}/api/projects/${projectId}/document-folder-config`, {
    method: 'POST',
    headers: { ...JSON_HEADERS, ...authHeaders() },
    body: JSON.stringify(request),
  });
  if (!response.ok) throw new Error(`Failed to save document folder config: ${response.status}`);
  return response.json();
}

export async function deleteDocumentFolderConfig(projectId: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/api/projects/${projectId}/document-folder-config`, {
    method: 'DELETE',
    headers: authHeaders(),
  });
  if (!response.ok) throw new Error(`Failed to delete document folder config: ${response.status}`);
}

// ── Human approval gate ──────────────────────────────────────────────────
// Throw-on-failure, deliberately never the listSkillSpecs swallow-to-mock idiom:
// a decision, its context, or its audit trail must never silently degrade into
// fabricated data.

export async function getApprovalGateContext(runId: string): Promise<ApprovalGateContext> {
  const response = await fetch(`${API_BASE_URL}/api/agent-runs/${runId}/approval-gate`, {
    headers: authHeaders(),
  });
  if (!response.ok) {
    throw new Error(await errorMessageFrom(response, 'Failed to load the approval gate'));
  }
  return response.json();
}

export async function submitApprovalDecision(
  runId: string,
  request: ApprovalDecisionRequest,
): Promise<ApprovalDecisionResult> {
  const response = await fetch(`${API_BASE_URL}/api/agent-runs/${runId}/approval-gate/decision`, {
    method: 'POST',
    headers: { ...JSON_HEADERS, ...authHeaders() },
    body: JSON.stringify(request),
  });
  if (!response.ok) {
    throw new Error(await errorMessageFrom(response, 'Failed to submit the approval decision'));
  }
  return response.json();
}

export async function listApprovalDecisions(runId: string): Promise<ApprovalDecisionAuditEntry[]> {
  const response = await fetch(`${API_BASE_URL}/api/agent-runs/${runId}/approval-decisions`, {
    headers: authHeaders(),
  });
  if (!response.ok) {
    throw new Error(await errorMessageFrom(response, 'Failed to load approval decisions'));
  }
  return response.json();
}

// ── Workflow execution history ───────────────────────────────────────────
// Throw-on-failure, deliberately never the listWorkflows swallow-to-[] idiom:
// a downstream failure must never silently look like "zero executions".

export async function listWorkflowExecutions(
  workflowId: string,
  params?: { limit?: number; offset?: number },
): Promise<WorkflowExecutionPage> {
  const query = new URLSearchParams();
  if (params?.limit !== undefined) query.set('limit', String(params.limit));
  if (params?.offset !== undefined) query.set('offset', String(params.offset));
  const queryString = query.toString();
  const response = await fetch(
    `${API_BASE_URL}/api/workflows/${workflowId}/executions${queryString ? `?${queryString}` : ''}`,
    { headers: authHeaders() },
  );
  if (!response.ok) {
    throw new Error(await errorMessageFrom(response, 'Failed to load workflow executions'));
  }
  return response.json();
}

export async function getWorkflowExecution(workflowId: string, runId: string): Promise<AgentRun> {
  const response = await fetch(`${API_BASE_URL}/api/workflows/${workflowId}/executions/${runId}`, {
    headers: authHeaders(),
  });
  if (!response.ok) {
    const message = await errorMessageFrom(response, 'Failed to load workflow execution');
    throw Object.assign(new Error(message), { status: response.status });
  }
  return response.json();
}
