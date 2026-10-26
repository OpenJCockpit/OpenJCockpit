import { describe, expect, it } from 'vitest';
import { candidateOrbTargets, workflowsForProject } from './workflowVisibility';
import type { WorkflowDefinition, WorkflowGroup } from './workflowTypes';

function workflow(id: string, projectName: string, groupId?: string): WorkflowDefinition {
  return {
    id,
    name: id,
    projectName,
    groupId,
    description: '',
    agentIds: [],
    subagentNames: [],
    skillNames: [],
    mcpTools: [],
    status: 'ACTIVE',
  };
}

const GROUPS: WorkflowGroup[] = [
  {
    id: 'wg-spec-workflow',
    name: 'Spec-driven development',
    description: '',
    projectName: '',
  },
  {
    id: 'wg-noordzee',
    name: 'Noordzee flows',
    description: '',
    projectName: 'Noordzee Logistics',
  },
];

const WORKFLOWS: WorkflowDefinition[] = [
  workflow('wf-spec-init', '', 'wg-spec-workflow'),
  workflow('wf-spec-create', '', 'wg-spec-workflow'),
  workflow('wf-spec-implement', '', 'wg-spec-workflow'),
  workflow('wf-own-project', 'Test Project'),
  workflow('wf-other-project', 'Other Project'),
  workflow('wf-grouped-noordzee', '', 'wg-noordzee'),
];

describe('workflowsForProject', () => {
  it('returns everything when no project is selected', () => {
    expect(workflowsForProject(WORKFLOWS, GROUPS, undefined)).toHaveLength(WORKFLOWS.length);
  });

  it('includes workflows of a global group for every project', () => {
    const forTest = workflowsForProject(WORKFLOWS, GROUPS, 'Test Project').map((w) => w.id);
    expect(forTest).toContain('wf-spec-init');
    expect(forTest).toContain('wf-spec-create');
    expect(forTest).toContain('wf-spec-implement');

    const forOther = workflowsForProject(WORKFLOWS, GROUPS, 'Completely Different Project').map(
      (w) => w.id,
    );
    expect(forOther).toEqual(['wf-spec-init', 'wf-spec-create', 'wf-spec-implement']);
  });

  it('matches workflows on their own project name', () => {
    const ids = workflowsForProject(WORKFLOWS, GROUPS, 'Test Project').map((w) => w.id);
    expect(ids).toContain('wf-own-project');
    expect(ids).not.toContain('wf-other-project');
  });

  it('falls back to the project of the group when the workflow has none', () => {
    const noordzee = workflowsForProject(WORKFLOWS, GROUPS, 'Noordzee Logistics').map((w) => w.id);
    expect(noordzee).toContain('wf-grouped-noordzee');

    const test = workflowsForProject(WORKFLOWS, GROUPS, 'Test Project').map((w) => w.id);
    expect(test).not.toContain('wf-grouped-noordzee');
  });

  it('lets the workflow project win over the group project', () => {
    const own = [workflow('wf-x', 'Test Project', 'wg-noordzee')];
    expect(workflowsForProject(own, GROUPS, 'Test Project')).toHaveLength(1);
    expect(workflowsForProject(own, GROUPS, 'Noordzee Logistics')).toHaveLength(0);
  });
});

describe('candidateOrbTargets', () => {
  const ids = (current: WorkflowDefinition) =>
    candidateOrbTargets(current, WORKFLOWS, GROUPS).map((w) => w.id);

  it('excludes the current workflow itself', () => {
    const current = WORKFLOWS[0]!;
    expect(ids(current)).not.toContain(current.id);
  });

  it('offers only global workflows to a global workflow', () => {
    expect(ids(WORKFLOWS[0]!)).toEqual(['wf-spec-create', 'wf-spec-implement']);
  });

  it('offers global and same-project workflows to a project workflow', () => {
    expect(ids(workflow('wf-me', 'Test Project'))).toEqual([
      'wf-spec-init',
      'wf-spec-create',
      'wf-spec-implement',
      'wf-own-project',
    ]);
  });

  it('resolves the current workflow project through its group', () => {
    expect(ids(workflow('wf-me', '', 'wg-noordzee'))).toContain('wf-grouped-noordzee');
    expect(ids(workflow('wf-me', '', 'wg-noordzee'))).not.toContain('wf-own-project');
  });
});
