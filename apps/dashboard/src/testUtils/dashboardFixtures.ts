import { vi } from 'vitest';
import * as api from '../api';
import * as workflowApi from '../workflow/workflowApi';
import { mockWorkspace } from '../mockWorkspace';
import type { Project, SpecFile } from '../types';
import type { WorkflowDefinition, WorkflowGroup } from '../workflow/workflowTypes';

export const PROJECT: Project = {
  id: 'project-1',
  name: 'Test Project',
  active: 1,
  newProject: 0,
  gitStatus: 'ACCESSIBLE',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
};

export const WORKFLOW: WorkflowDefinition = {
  id: 'wf-1',
  name: 'Spec to pull request',
  projectName: 'Test Project',
  description: 'Executes the spec',
  agentIds: ['agent-1'],
  subagentNames: [],
  skillNames: [],
  mcpTools: [],
  status: 'ACTIVE',
};

interface DashboardMockOptions {
  project?: Project;
  specs?: SpecFile[];
  workflows?: WorkflowDefinition[];
  groups?: WorkflowGroup[];
}

/** Selects PROJECT and stubs the api modules (mocked via `vi.mock` in the test file) for App. */
export function mockDashboardApis({
  project = PROJECT,
  specs = mockWorkspace.specs,
  workflows = [WORKFLOW],
  groups = [],
}: DashboardMockOptions = {}) {
  localStorage.setItem('openjcockpit_project_id', project.id);
  vi.mocked(api.loadProjects).mockResolvedValue([project]);
  vi.mocked(api.loadWorkspace).mockResolvedValue(mockWorkspace);
  vi.mocked(api.loadAgentDefinitions).mockResolvedValue([]);
  vi.mocked(api.loadProjectSpecs).mockResolvedValue(specs);
  vi.mocked(workflowApi.listWorkflows).mockResolvedValue(workflows);
  vi.mocked(workflowApi.listWorkflowGroups).mockResolvedValue(groups);
}
