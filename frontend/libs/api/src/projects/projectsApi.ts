import { errorMessageFrom } from '../core/httpErrors';
import { ApiError, API_BASE_URL, apiRequest, authHeaders } from '../core/http';
import type {
  Project,
  ProjectGitCredentialDto,
  ProjectGitCredentialRequest,
  ProjectGitStatusDto,
  ProjectRequest,
} from '../types/types';

export function loadProjects(signal?: AbortSignal): Promise<Project[]> {
  return apiRequest('/api/projects', { signal, errorMessage: 'Failed to load projects' });
}

export function loadAllProjects(signal?: AbortSignal): Promise<Project[]> {
  return apiRequest('/api/projects/all', { signal, errorMessage: 'Failed to load projects' });
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
    throw new ApiError(
      await errorMessageFrom(response, 'Failed to save git credentials'),
      response.status,
    );
  }
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
