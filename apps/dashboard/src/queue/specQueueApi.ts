import { API_BASE_URL, authHeaders } from '../api';
import type { SpecFile } from '../types';
import { errorMessageFrom } from '../workflow/workflowApi';
import type {
  SpecQueue,
  SpecQueueEnqueueRequest,
  SpecQueueItem,
  SpecQueueItemUpdateRequest,
  SpecQueueSettings,
} from './specQueueTypes';

type Method = 'GET' | 'POST' | 'PATCH' | 'PUT' | 'DELETE';

function queueUrl(projectId: string, suffix = ''): string {
  return `${API_BASE_URL}/api/projects/${encodeURIComponent(projectId)}/spec-queue${suffix}`;
}

function itemUrl(projectId: string, itemId: string, suffix = ''): string {
  return queueUrl(projectId, `/items/${encodeURIComponent(itemId)}${suffix}`);
}

function init(method: Method, body?: unknown): RequestInit {
  if (body === undefined) return { method, headers: authHeaders() };
  return {
    method,
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify(body),
  };
}

async function request<T>(
  url: string,
  method: Method,
  fallback: string,
  body?: unknown,
): Promise<T> {
  const response = await fetch(url, init(method, body));
  if (!response.ok) throw new Error(await errorMessageFrom(response, fallback));
  return response.json();
}

export function getSpecQueue(projectId: string): Promise<SpecQueue> {
  return request(queueUrl(projectId), 'GET', 'Failed to load the spec queue');
}

export function enqueueSpecQueueItem(
  projectId: string,
  body: SpecQueueEnqueueRequest,
): Promise<SpecQueueItem> {
  return request(
    queueUrl(projectId, '/items'),
    'POST',
    'Failed to add the spec to the queue',
    body,
  );
}

export function updateSpecQueueItem(
  projectId: string,
  itemId: string,
  body: SpecQueueItemUpdateRequest,
): Promise<SpecQueueItem> {
  return request(itemUrl(projectId, itemId), 'PATCH', 'Failed to update the queue item', body);
}

export function removeSpecQueueItem(projectId: string, itemId: string): Promise<SpecQueueItem> {
  return request(itemUrl(projectId, itemId), 'DELETE', 'Failed to remove the queue item');
}

export function reorderSpecQueue(projectId: string, itemIds: string[]): Promise<SpecQueue> {
  return request(queueUrl(projectId, '/order'), 'PUT', 'Failed to reorder the queue', { itemIds });
}

export function pauseSpecQueue(projectId: string): Promise<SpecQueue> {
  return request(queueUrl(projectId, '/pause'), 'POST', 'Failed to pause the queue');
}

export function resumeSpecQueue(projectId: string): Promise<SpecQueue> {
  return request(queueUrl(projectId, '/resume'), 'POST', 'Failed to resume the queue');
}

export function retrySpecQueueItem(projectId: string, itemId: string): Promise<SpecQueueItem> {
  return request(itemUrl(projectId, itemId, '/retry'), 'POST', 'Failed to retry the queue item');
}

export function skipSpecQueueItem(projectId: string, itemId: string): Promise<SpecQueueItem> {
  return request(itemUrl(projectId, itemId, '/skip'), 'POST', 'Failed to skip the queue item');
}

export function getSpecQueueSettings(projectId: string): Promise<SpecQueueSettings> {
  return request(queueUrl(projectId, '/settings'), 'GET', 'Failed to load the queue settings');
}

export async function updateSpecQueueSettings(
  projectId: string,
  autoMergeAllowed: boolean,
): Promise<SpecQueueSettings> {
  const response = await fetch(queueUrl(projectId, '/settings'), init('PUT', { autoMergeAllowed }));
  if (!response.ok) {
    // errorMessageFrom prefers the server's own message; the fallback covers a bodiless 403.
    const fallback =
      response.status === 403
        ? 'Only administrators can change this setting.'
        : 'Failed to update the queue settings';
    throw new Error(await errorMessageFrom(response, fallback));
  }
  return response.json();
}

// Unlike loadProjectSpecs, this does not swallow errors: the dialog must tell a failed
// listing (502) apart from an empty repository.
export async function listSpecFilesStrict(projectId: string): Promise<SpecFile[]> {
  const response = await fetch(
    `${API_BASE_URL}/api/projects/${encodeURIComponent(projectId)}/spec-files`,
    { headers: authHeaders() },
  );
  if (!response.ok) throw new Error(await errorMessageFrom(response, 'Failed to load spec files'));
  return response.json();
}
