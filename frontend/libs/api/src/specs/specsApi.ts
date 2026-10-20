import { errorMessageFrom } from '../core/httpErrors';
import { API_BASE_URL, authHeaders } from '../core/http';
import type { SpecFile, SpecInitResult, SpecInitStatus } from '../types/types';

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
