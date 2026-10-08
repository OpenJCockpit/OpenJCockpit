import { describe, expect, it } from 'vitest';
import { workflowsForProject } from './workflowVisibility';
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
  { id: 'wg-spec-workflow', name: 'Spec-driven development', description: '', projectName: '' },
  { id: 'wg-noordzee', name: 'Noordzee flows', description: '', projectName: 'Noordzee Logistics' },
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
