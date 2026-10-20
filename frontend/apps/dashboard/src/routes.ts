export const ROUTES = {
  landing: '/',
  projects: '/projects',
  dashboard: '/dashboard',
  settings: '/settings',
  workflow: '/workflow',
  specFiles: '/spec-files',
  specQueue: '/spec-queue',
  skillsHub: '/skills-hub',
  skillsHubSettings: '/skills-hub/settings',
} as const;

export function workflowRunPath(workflowId: string, runId: string) {
  return `${ROUTES.workflow}/${encodeURIComponent(workflowId)}/runs/${encodeURIComponent(runId)}`;
}
