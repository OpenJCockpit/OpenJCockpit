import { apiRequest, apiRequestVoid } from '../core/http';
import type { AgentRun, PreflightResult, Workspace } from '../types/types';

export function loadWorkspace(customerId: string, signal?: AbortSignal): Promise<Workspace> {
  return apiRequest(`/api/workspaces/${customerId}`, {
    signal,
    errorMessage: 'Failed to load workspace',
  });
}

export function checkWorkflowPreflight(projectId: string): Promise<PreflightResult> {
  return apiRequest(`/api/agentic-workflows/preflight?projectId=${projectId}`, {
    method: 'POST',
    errorMessage: 'Failed to run pre-flight checks',
  });
}

export function getAgentRun(runId: string): Promise<AgentRun> {
  return apiRequest(`/api/agent-runs/${runId}`, { errorMessage: 'Failed to get agent run' });
}

export function stopAgentRun(runId: string): Promise<void> {
  return apiRequestVoid(`/api/agent-runs/${runId}`, {
    method: 'DELETE',
    errorMessage: 'Failed to stop agent run',
  });
}
