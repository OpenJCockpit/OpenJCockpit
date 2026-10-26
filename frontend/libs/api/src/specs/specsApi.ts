import { apiRequest } from '../core/http';
import type { SpecFile, SpecInitResult, SpecInitStatus } from '../types/types';

export function loadProjectSpecs(projectId: string, signal?: AbortSignal): Promise<SpecFile[]> {
  return apiRequest(`/api/projects/${projectId}/spec-files`, {
    signal,
    errorMessage: 'Failed to load spec files',
  });
}

export function getSpecInitStatus(
  projectId: string,
  signal?: AbortSignal,
): Promise<SpecInitStatus | null> {
  return apiRequest(`/api/projects/${projectId}/spec-init/status`, {
    signal,
    errorMessage: 'Failed to check spec-init status',
  });
}

export function initSpecFolder(projectId: string): Promise<SpecInitResult> {
  return apiRequest(`/api/projects/${projectId}/spec-init`, {
    method: 'POST',
    errorMessage: 'Failed to initialize spec folder',
  });
}

export function saveSpecFile(
  projectId: string,
  fileName: string,
  content: string,
): Promise<SpecInitResult> {
  return apiRequest(`/api/projects/${projectId}/spec-files`, {
    method: 'PUT',
    body: { fileName, content },
    errorMessage: 'Failed to save spec file',
  });
}
