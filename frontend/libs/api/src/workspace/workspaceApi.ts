import { API_BASE_URL, authHeaders } from '../core/http';
import type { AgentRun, PreflightResult, Workspace } from '../types/types';
import { mockWorkspace } from './mockWorkspace';

export async function loadWorkspace(customerId: string): Promise<Workspace> {
  try {
    const response = await fetch(`${API_BASE_URL}/api/workspaces/${customerId}`, {
      headers: authHeaders(),
    });
    if (!response.ok) {
      console.warn('Falling back to mock workspace data: status', response.status);
      return mockWorkspace;
    }
    return await response.json();
  } catch (error) {
    console.warn('Falling back to mock workspace data', error);
    return mockWorkspace;
  }
}

export async function checkWorkflowPreflight(projectId: string): Promise<PreflightResult> {
  const response = await fetch(
    `${API_BASE_URL}/api/agentic-workflows/preflight?projectId=${projectId}`,
    {
      method: 'POST',
      headers: authHeaders(),
    },
  );
  if (!response.ok) throw new Error(`Failed to run pre-flight checks: ${response.status}`);
  return response.json();
}

export async function getAgentRun(runId: string): Promise<AgentRun> {
  const response = await fetch(`${API_BASE_URL}/api/agent-runs/${runId}`, {
    headers: authHeaders(),
  });
  if (!response.ok) throw new Error(`Failed to get agent run: ${response.status}`);
  return response.json();
}

export async function stopAgentRun(runId: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/api/agent-runs/${runId}`, {
    method: 'DELETE',
    headers: authHeaders(),
  });
  if (!response.ok) throw new Error(`Failed to stop agent run: ${response.status}`);
}
