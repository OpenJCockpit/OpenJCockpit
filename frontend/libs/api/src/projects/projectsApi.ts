import { apiRequest, apiRequestVoid } from '../core/http';
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

export function createProject(req: ProjectRequest): Promise<Project> {
  return apiRequest('/api/projects', {
    method: 'POST',
    body: req,
    errorMessage: 'Failed to create project',
  });
}

export function updateProject(id: string, req: ProjectRequest): Promise<Project> {
  return apiRequest(`/api/projects/${id}`, {
    method: 'PUT',
    body: req,
    errorMessage: 'Failed to update project',
  });
}

export function activateProject(id: string): Promise<void> {
  return apiRequestVoid(`/api/projects/${id}/activate`, {
    method: 'PATCH',
    errorMessage: 'Failed to activate project',
  });
}

export function deactivateProject(id: string): Promise<void> {
  return apiRequestVoid(`/api/projects/${id}/deactivate`, {
    method: 'PATCH',
    errorMessage: 'Failed to deactivate project',
  });
}

export function patchNewProject(id: string, newProject: number): Promise<void> {
  return apiRequestVoid(`/api/projects/${id}/new-project`, {
    method: 'PATCH',
    body: { newProject },
    errorMessage: 'Failed to update new-project flag',
  });
}

export function getGitCredentials(id: string): Promise<ProjectGitCredentialDto> {
  return apiRequest(`/api/projects/${id}/git-credentials`, {
    errorMessage: 'Failed to load git credentials',
  });
}

export function saveGitCredentials(id: string, req: ProjectGitCredentialRequest): Promise<void> {
  return apiRequestVoid(`/api/projects/${id}/git-credentials`, {
    method: 'POST',
    body: req,
    errorMessage: 'Failed to save git credentials',
  });
}

export function deleteGitCredentials(id: string): Promise<void> {
  return apiRequestVoid(`/api/projects/${id}/git-credentials`, {
    method: 'DELETE',
    errorMessage: 'Failed to delete git credentials',
  });
}

export function triggerGitCheck(id: string): Promise<ProjectGitStatusDto> {
  return apiRequest(`/api/projects/${id}/git-check`, {
    method: 'POST',
    errorMessage: 'Failed to trigger git check',
  });
}

export function getGitStatus(id: string): Promise<ProjectGitStatusDto> {
  return apiRequest(`/api/projects/${id}/git-status`, {
    errorMessage: 'Failed to get git status',
  });
}
