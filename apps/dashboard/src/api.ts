import {
  AgentDefinition,
  AgentRun,
  PreflightResult,
  Project,
  ProjectGitCredentialDto,
  ProjectGitCredentialRequest,
  ProjectGitStatusDto,
  ProjectRequest,
  SkillsMarketplaceConnection,
  SkillsMarketplaceConnectionRequest,
  SpecFile,
  SpecInitResult,
  SpecInitStatus,
  Workspace,
} from './types';
import { mockWorkspace } from './mockWorkspace';

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '';

let _authToken: string | null = null;

export function setAuthToken(token: string) {
  _authToken = token;
}

export function authHeaders(): HeadersInit {
  return _authToken ? { Authorization: `Bearer ${_authToken}` } : {};
}

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

export async function fetchAgentDefinitions(): Promise<AgentDefinition[]> {
  const response = await fetch(`${API_BASE_URL}/api/agents`, {
    headers: authHeaders(),
  });
  if (!response.ok) {
    throw new Error(`Failed to load agent definitions: ${response.status}`);
  }
  return await response.json();
}

export async function loadAgentDefinitions(): Promise<AgentDefinition[]> {
  try {
    return await fetchAgentDefinitions();
  } catch (error) {
    console.warn('Could not load agent definitions', error);
    return [];
  }
}

export async function startAgentRun(
  customerId: string,
  specContent: string,
  agentIds: string[],
  repositoryUrl: string,
): Promise<AgentRun> {
  const response = await fetch(`${API_BASE_URL}/api/agent-runs`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify({
      customerId,
      specFile: specContent,
      agentIds,
      requestedBy: 'user',
      repositoryUrl,
    }),
  });
  if (!response.ok) throw new Error(`Failed to start agent run: ${response.status}`);
  return response.json();
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

const PROJECT_KEY = 'openjcockpit_project_id';

export function getStoredProjectId(): string | null {
  return localStorage.getItem(PROJECT_KEY);
}
export function setStoredProjectId(id: string): void {
  localStorage.setItem(PROJECT_KEY, id);
}
export function clearStoredProjectId(): void {
  localStorage.removeItem(PROJECT_KEY);
}

export async function loadProjects(): Promise<Project[]> {
  try {
    const response = await fetch(`${API_BASE_URL}/api/projects`, { headers: authHeaders() });
    if (!response.ok) return [];
    return await response.json();
  } catch {
    return [];
  }
}

export async function loadAllProjects(): Promise<Project[]> {
  try {
    const response = await fetch(`${API_BASE_URL}/api/projects/all`, { headers: authHeaders() });
    if (!response.ok) return [];
    return await response.json();
  } catch {
    return [];
  }
}

export async function createProject(req: ProjectRequest): Promise<Project> {
  const response = await fetch(`${API_BASE_URL}/api/projects`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify(req),
  });
  if (!response.ok) throw new Error(`Failed to create project: ${response.status}`);
  return response.json();
}

export async function updateProject(id: string, req: ProjectRequest): Promise<Project> {
  const response = await fetch(`${API_BASE_URL}/api/projects/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify(req),
  });
  if (!response.ok) throw new Error(`Failed to update project: ${response.status}`);
  return response.json();
}

export async function activateProject(id: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/api/projects/${id}/activate`, {
    method: 'PATCH',
    headers: authHeaders(),
  });
  if (!response.ok) throw new Error(`Failed to activate project: ${response.status}`);
}

export async function deactivateProject(id: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/api/projects/${id}/deactivate`, {
    method: 'PATCH',
    headers: authHeaders(),
  });
  if (!response.ok) throw new Error(`Failed to deactivate project: ${response.status}`);
}

export async function patchNewProject(id: string, newProject: number): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/api/projects/${id}/new-project`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify({ newProject }),
  });
  if (!response.ok) throw new Error(`Failed to update new-project flag: ${response.status}`);
}

export async function getGitCredentials(id: string): Promise<ProjectGitCredentialDto> {
  const response = await fetch(`${API_BASE_URL}/api/projects/${id}/git-credentials`, {
    headers: authHeaders(),
  });
  if (!response.ok) throw new Error(`Failed to load git credentials: ${response.status}`);
  return response.json();
}

export async function saveGitCredentials(
  id: string,
  req: ProjectGitCredentialRequest,
): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/api/projects/${id}/git-credentials`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify(req),
  });
  if (!response.ok) {
    throw new Error(await errorMessageFrom(response, 'Failed to save git credentials'));
  }
}

async function errorMessageFrom(response: Response, fallback: string): Promise<string> {
  try {
    const body = await response.json();
    if (body && typeof body.message === 'string' && body.message.trim()) return body.message;
  } catch {
    // response body wasn't JSON (or was empty) — fall back below
  }
  return `${fallback}: ${response.status}`;
}

export async function deleteGitCredentials(id: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/api/projects/${id}/git-credentials`, {
    method: 'DELETE',
    headers: authHeaders(),
  });
  if (!response.ok) throw new Error(`Failed to delete git credentials: ${response.status}`);
}

export async function triggerGitCheck(id: string): Promise<ProjectGitStatusDto> {
  const response = await fetch(`${API_BASE_URL}/api/projects/${id}/git-check`, {
    method: 'POST',
    headers: authHeaders(),
  });
  if (!response.ok) throw new Error(`Failed to trigger git check: ${response.status}`);
  return response.json();
}

export async function getGitStatus(id: string): Promise<ProjectGitStatusDto> {
  const response = await fetch(`${API_BASE_URL}/api/projects/${id}/git-status`, {
    headers: authHeaders(),
  });
  if (!response.ok) throw new Error(`Failed to get git status: ${response.status}`);
  return response.json();
}

export async function loadProjectSpecs(projectId: string): Promise<SpecFile[]> {
  try {
    const response = await fetch(`${API_BASE_URL}/api/projects/${projectId}/spec-files`, {
      headers: authHeaders(),
    });
    if (!response.ok) {
      console.warn('Could not load spec files from repository: status', response.status);
      return [];
    }
    return await response.json();
  } catch (error) {
    console.warn('Could not load spec files from repository', error);
    return [];
  }
}

export async function getSpecInitStatus(projectId: string): Promise<SpecInitStatus | null> {
  try {
    const response = await fetch(`${API_BASE_URL}/api/projects/${projectId}/spec-init/status`, {
      headers: authHeaders(),
    });
    if (!response.ok) {
      console.warn('Could not check spec-init status: status', response.status);
      return null;
    }
    return await response.json();
  } catch (error) {
    console.warn('Could not check spec-init status', error);
    return null;
  }
}

export async function initSpecFolder(projectId: string): Promise<SpecInitResult> {
  const response = await fetch(`${API_BASE_URL}/api/projects/${projectId}/spec-init`, {
    method: 'POST',
    headers: authHeaders(),
  });
  if (!response.ok) {
    throw new Error(await errorMessageFrom(response, 'Failed to initialize spec folder'));
  }
  return response.json();
}

export async function saveSpecFile(
  projectId: string,
  fileName: string,
  content: string,
): Promise<SpecInitResult> {
  const response = await fetch(`${API_BASE_URL}/api/projects/${projectId}/spec-files`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify({ fileName, content }),
  });
  if (!response.ok) throw new Error(await errorMessageFrom(response, 'Failed to save spec file'));
  return response.json();
}

// listSkillsMarketplaces intentionally throws on failure (unlike loadProjects' swallow-to-[]
// idiom) — an empty array must mean "genuinely zero marketplaces", never "request failed",
// so the Skills Hub Settings empty state is never shown for a transport or auth error.
export async function listSkillsMarketplaces(): Promise<SkillsMarketplaceConnection[]> {
  const response = await fetch(`${API_BASE_URL}/api/skills-marketplaces`, {
    headers: authHeaders(),
  });
  if (!response.ok) throw new Error(await errorMessageFrom(response, 'Failed to load marketplaces'));
  return response.json();
}

export async function createSkillsMarketplace(
  req: SkillsMarketplaceConnectionRequest,
): Promise<SkillsMarketplaceConnection> {
  const response = await fetch(`${API_BASE_URL}/api/skills-marketplaces`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify(req),
  });
  if (!response.ok) {
    throw new Error(await errorMessageFrom(response, 'Failed to create marketplace'));
  }
  return response.json();
}

export async function updateSkillsMarketplace(
  id: string,
  req: SkillsMarketplaceConnectionRequest,
): Promise<SkillsMarketplaceConnection> {
  const response = await fetch(`${API_BASE_URL}/api/skills-marketplaces/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify(req),
  });
  if (!response.ok) {
    throw new Error(await errorMessageFrom(response, 'Failed to update marketplace'));
  }
  return response.json();
}

export async function deleteSkillsMarketplace(id: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/api/skills-marketplaces/${id}`, {
    method: 'DELETE',
    headers: authHeaders(),
  });
  if (!response.ok) {
    throw new Error(await errorMessageFrom(response, 'Failed to delete marketplace'));
  }
}
