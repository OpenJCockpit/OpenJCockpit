import { apiRequest } from '../core/http';
import type { SpecFile } from '../types/types';
import type {
  SpecQueue,
  SpecQueueEnqueueRequest,
  SpecQueueItem,
  SpecQueueItemUpdateRequest,
  SpecQueueSettings,
} from './specQueueTypes';

type Method = 'GET' | 'POST' | 'PATCH' | 'PUT' | 'DELETE';

function queuePath(projectId: string, suffix = ''): string {
  return `/api/projects/${encodeURIComponent(projectId)}/spec-queue${suffix}`;
}

function itemPath(projectId: string, itemId: string, suffix = ''): string {
  return queuePath(projectId, `/items/${encodeURIComponent(itemId)}${suffix}`);
}

function request<T>(
  path: string,
  method: Method,
  errorMessage: string | ((status: number) => string),
  body?: unknown,
): Promise<T> {
  return apiRequest(path, { method, body, errorMessage });
}

export function getSpecQueue(projectId: string): Promise<SpecQueue> {
  return request(queuePath(projectId), 'GET', 'Failed to load the spec queue');
}

export function enqueueSpecQueueItem(
  projectId: string,
  body: SpecQueueEnqueueRequest,
): Promise<SpecQueueItem> {
  return request(
    queuePath(projectId, '/items'),
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
  return request(itemPath(projectId, itemId), 'PATCH', 'Failed to update the queue item', body);
}

export function removeSpecQueueItem(projectId: string, itemId: string): Promise<SpecQueueItem> {
  return request(itemPath(projectId, itemId), 'DELETE', 'Failed to remove the queue item');
}

export function reorderSpecQueue(projectId: string, itemIds: string[]): Promise<SpecQueue> {
  return request(queuePath(projectId, '/order'), 'PUT', 'Failed to reorder the queue', { itemIds });
}

export function pauseSpecQueue(projectId: string): Promise<SpecQueue> {
  return request(queuePath(projectId, '/pause'), 'POST', 'Failed to pause the queue');
}

export function resumeSpecQueue(projectId: string): Promise<SpecQueue> {
  return request(queuePath(projectId, '/resume'), 'POST', 'Failed to resume the queue');
}

export function retrySpecQueueItem(projectId: string, itemId: string): Promise<SpecQueueItem> {
  return request(itemPath(projectId, itemId, '/retry'), 'POST', 'Failed to retry the queue item');
}

export function skipSpecQueueItem(projectId: string, itemId: string): Promise<SpecQueueItem> {
  return request(itemPath(projectId, itemId, '/skip'), 'POST', 'Failed to skip the queue item');
}

export function getSpecQueueSettings(projectId: string): Promise<SpecQueueSettings> {
  return request(queuePath(projectId, '/settings'), 'GET', 'Failed to load the queue settings');
}

export function updateSpecQueueSettings(
  projectId: string,
  autoMergeAllowed: boolean,
): Promise<SpecQueueSettings> {
  // The server's own message wins; the fallback covers a bodiless 403.
  return request(
    queuePath(projectId, '/settings'),
    'PUT',
    (status) =>
      status === 403
        ? 'Only administrators can change this setting.'
        : 'Failed to update the queue settings',
    { autoMergeAllowed },
  );
}

// Unlike loadProjectSpecs, this does not swallow errors: the dialog must tell a failed
// listing (502) apart from an empty repository.
export function listSpecFilesStrict(projectId: string): Promise<SpecFile[]> {
  return apiRequest(`/api/projects/${encodeURIComponent(projectId)}/spec-files`, {
    errorMessage: 'Failed to load spec files',
  });
}
