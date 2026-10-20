import type { WorkflowDefinition, WorkflowGroup } from './workflowTypes';

/**
 * Resolve a workflow's effective project: the workflow's own project wins;
 * otherwise the project of its group decides. Empty result means global.
 */
function resolveEffectiveProject(w: WorkflowDefinition, groups: WorkflowGroup[]): string {
  return w.projectName || groups.find((g) => g.id === w.groupId)?.projectName || '';
}

/**
 * Which workflows are available for a project: the workflow's own project wins;
 * otherwise the project of its group decides. A workflow whose effective project
 * is empty (e.g. the seeded Spec-driven development group, which has no project)
 * is global and available for every project.
 */
export function workflowsForProject(
  workflows: WorkflowDefinition[],
  groups: WorkflowGroup[],
  projectName?: string,
): WorkflowDefinition[] {
  if (!projectName) return workflows;
  return workflows.filter((w) => {
    const effectiveProject = resolveEffectiveProject(w, groups);
    return !effectiveProject || effectiveProject === projectName;
  });
}

/**
 * Returns the list of workflows legally referenceable as an orb target from
 * `current`. This is a USABILITY hint only, not authoritative — the server
 * enforces the real same-project/global rule; this filter must not be
 * presented anywhere as validation.
 *
 * Rules:
 * - `current` itself is excluded (no self-reference).
 * - If `current` is global (empty effective project), only global candidates are legal.
 * - If `current` has effective project P, legal candidates are: any global workflow
 *   (empty effective project), OR any workflow whose effective project equals P.
 */
export function candidateOrbTargets(
  current: WorkflowDefinition,
  workflows: WorkflowDefinition[],
  groups: WorkflowGroup[],
): WorkflowDefinition[] {
  const currentProject = resolveEffectiveProject(current, groups);
  return workflows.filter((w) => {
    if (w.id === current.id) return false;
    const candidateProject = resolveEffectiveProject(w, groups);
    if (!currentProject) {
      // current is global → only global candidates are legal
      return !candidateProject;
    }
    // current has project P → global candidates OR same-project candidates
    return !candidateProject || candidateProject === currentProject;
  });
}
