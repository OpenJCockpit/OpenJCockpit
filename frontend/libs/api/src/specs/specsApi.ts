import { errorMessageFrom } from '../core/httpErrors';
import { ApiError, API_BASE_URL, apiRequest, authHeaders } from '../core/http';
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

export async function initSpecFolder(projectId: string): Promise<SpecInitResult> {
  const response = await fetch(`${API_BASE_URL}/api/projects/${projectId}/spec-init`, {
    method: 'POST',
    headers: authHeaders(),
  });
  if (!response.ok) {
    throw new ApiError(
      await errorMessageFrom(response, 'Failed to initialize spec folder'),
      response.status,
    );
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
  if (!response.ok)
    throw new ApiError(
      await errorMessageFrom(response, 'Failed to save spec file'),
      response.status,
    );
  return response.json();
}
