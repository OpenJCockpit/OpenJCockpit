export const SPEC_FILES_ROUTE = '/spec-files';
export const SPEC_QUEUE_ROUTE = '/spec-queue';
export const WORKFLOW_ROUTE = '/workflow';

export function workflowRunPath(workflowId: string, runId: string) {
  return `${WORKFLOW_ROUTE}/${encodeURIComponent(workflowId)}/runs/${encodeURIComponent(runId)}`;
}
