import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setAuthToken } from '../core/http';
import * as api from './workflowApi';

const mockFetch = vi.fn();

function ok(body?: unknown) {
  return { ok: true, status: 200, json: () => Promise.resolve(body) };
}
function fail(status: number, body?: unknown) {
  return {
    ok: false,
    status,
    json: () => (body ? Promise.resolve(body) : Promise.reject(new Error('not json'))),
  };
}

const payload = { name: 'x' } as never;

interface Case {
  name: string;
  call: () => Promise<unknown>;
  url: string;
  method?: string;
  body?: unknown;
  /** Failure message prefix; the thrown message is "<prefix>: <status>". */
  error: string;
}

const cases: Case[] = [
  {
    name: 'getWorkflow',
    call: () => api.getWorkflow('w1'),
    url: '/api/workflows/w1',
    error: 'Failed to load workflow',
  },
  {
    name: 'createWorkflow',
    call: () => api.createWorkflow(payload),
    url: '/api/workflows',
    method: 'POST',
    body: payload,
    error: 'Failed to create workflow',
  },
  {
    name: 'updateWorkflow',
    call: () => api.updateWorkflow('w1', payload),
    url: '/api/workflows/w1',
    method: 'PUT',
    body: payload,
    error: 'Failed to update workflow',
  },
  {
    name: 'deleteWorkflow',
    call: () => api.deleteWorkflow('w1'),
    url: '/api/workflows/w1',
    method: 'DELETE',
    error: 'Failed to delete workflow',
  },
  {
    name: 'listWorkflowGroups',
    call: () => api.listWorkflowGroups(),
    url: '/api/workflow-groups',
    error: 'Failed to load workflow groups',
  },
  {
    name: 'createWorkflowGroup',
    call: () => api.createWorkflowGroup(payload),
    url: '/api/workflow-groups',
    method: 'POST',
    body: payload,
    error: 'Failed to create workflow group',
  },
  {
    name: 'updateWorkflowGroup',
    call: () => api.updateWorkflowGroup('g1', payload),
    url: '/api/workflow-groups/g1',
    method: 'PUT',
    body: payload,
    error: 'Failed to update workflow group',
  },
  {
    name: 'deleteWorkflowGroup',
    call: () => api.deleteWorkflowGroup('g1'),
    url: '/api/workflow-groups/g1',
    method: 'DELETE',
    error: 'Failed to delete workflow group',
  },
  {
    name: 'exportWorkflows',
    call: () => api.exportWorkflows(),
    url: '/api/workflows/export',
    error: 'Failed to export workflows',
  },
  {
    name: 'importWorkflows',
    call: () => api.importWorkflows(payload),
    url: '/api/workflows/import',
    method: 'POST',
    body: payload,
    error: 'Failed to import workflows',
  },
  {
    name: 'getOpaConfig',
    call: () => api.getOpaConfig(),
    url: '/api/opa-config',
    error: 'Failed to load OPA config',
  },
  {
    name: 'saveOpaConfig',
    call: () => api.saveOpaConfig(payload),
    url: '/api/opa-config',
    method: 'PUT',
    body: payload,
    error: 'Failed to save OPA config',
  },
  {
    name: 'getAgentCatalog',
    call: () => api.getAgentCatalog(),
    url: '/api/agent-catalog',
    error: 'Failed to load agent catalog',
  },
  {
    name: 'getProviderAgentCard',
    call: () => api.getProviderAgentCard('lite llm', 'a/b'),
    url: '/api/agent-catalog/providers/lite%20llm/agents/a%2Fb/card',
    error: 'Failed to load agent card',
  },
  {
    name: 'createAgentSpec',
    call: () => api.createAgentSpec(payload),
    url: '/api/agent-definitions',
    method: 'POST',
    body: payload,
    error: 'Failed to create agent definition',
  },
  {
    name: 'updateAgentSpec',
    call: () => api.updateAgentSpec('a1', payload),
    url: '/api/agent-definitions/a1',
    method: 'PUT',
    body: payload,
    error: 'Failed to update agent definition',
  },
  {
    name: 'deleteAgentSpec',
    call: () => api.deleteAgentSpec('a1'),
    url: '/api/agent-definitions/a1',
    method: 'DELETE',
    error: 'Failed to delete agent definition',
  },
  {
    name: 'generateAgentSpec',
    call: () => api.generateAgentSpec('do it'),
    url: '/api/agent-definitions/generate-from-prompt',
    method: 'POST',
    body: { prompt: 'do it' },
    error: 'Failed to generate agent definition',
  },
  {
    name: 'listSubagentSpecs',
    call: () => api.listSubagentSpecs(),
    url: '/api/subagent-definitions',
    error: 'Failed to load subagent definitions',
  },
  {
    name: 'createSubagentSpec',
    call: () => api.createSubagentSpec(payload),
    url: '/api/subagent-definitions',
    method: 'POST',
    body: payload,
    error: 'Failed to create subagent definition',
  },
  {
    name: 'updateSubagentSpec',
    call: () => api.updateSubagentSpec('s1', payload),
    url: '/api/subagent-definitions/s1',
    method: 'PUT',
    body: payload,
    error: 'Failed to update subagent definition',
  },
  {
    name: 'deleteSubagentSpec',
    call: () => api.deleteSubagentSpec('s1'),
    url: '/api/subagent-definitions/s1',
    method: 'DELETE',
    error: 'Failed to delete subagent definition',
  },
  {
    name: 'generateSubagentSpec',
    call: () => api.generateSubagentSpec('do it'),
    url: '/api/subagent-definitions/generate-from-prompt',
    method: 'POST',
    body: { prompt: 'do it' },
    error: 'Failed to generate subagent definition',
  },
  {
    name: 'listSkillSpecs',
    call: () => api.listSkillSpecs(),
    url: '/api/skill-definitions',
    error: 'Failed to load skill definitions',
  },
  {
    name: 'createSkillSpec',
    call: () => api.createSkillSpec(payload),
    url: '/api/skill-definitions',
    method: 'POST',
    body: payload,
    error: 'Failed to create skill definition',
  },
  {
    name: 'updateSkillSpec',
    call: () => api.updateSkillSpec('k1', payload),
    url: '/api/skill-definitions/k1',
    method: 'PUT',
    body: payload,
    error: 'Failed to update skill definition',
  },
  {
    name: 'deleteSkillSpec',
    call: () => api.deleteSkillSpec('k1'),
    url: '/api/skill-definitions/k1',
    method: 'DELETE',
    error: 'Failed to delete skill definition',
  },
  {
    name: 'generateSkillSpec',
    call: () => api.generateSkillSpec('do it'),
    url: '/api/skill-definitions/generate-from-prompt',
    method: 'POST',
    body: { prompt: 'do it' },
    error: 'Failed to generate skill definition',
  },
  {
    name: 'listA2AServices',
    call: () => api.listA2AServices(),
    url: '/api/a2a-services',
    error: 'Failed to load A2A services',
  },
  {
    name: 'createA2AService',
    call: () => api.createA2AService(payload),
    url: '/api/a2a-services',
    method: 'POST',
    body: payload,
    error: 'Failed to add A2A service',
  },
  {
    name: 'updateA2AService',
    call: () => api.updateA2AService('a/1', payload),
    url: '/api/a2a-services/a%2F1',
    method: 'PUT',
    body: payload,
    error: 'Failed to save A2A service',
  },
  {
    name: 'deleteA2AService',
    call: () => api.deleteA2AService('a/1'),
    url: '/api/a2a-services/a%2F1',
    method: 'DELETE',
    error: 'Failed to remove A2A service',
  },
  {
    name: 'getSkillCatalog',
    call: () => api.getSkillCatalog(),
    url: '/api/skill-catalog',
    error: 'Failed to load skill catalog',
  },
  {
    name: 'getHermesConfig',
    call: () => api.getHermesConfig('p1'),
    url: '/api/projects/p1/hermes-config',
    error: 'Failed to load Hermes config',
  },
  {
    name: 'saveHermesConfig',
    call: () => api.saveHermesConfig('p1', payload),
    url: '/api/projects/p1/hermes-config',
    method: 'POST',
    body: payload,
    error: 'Failed to save Hermes config',
  },
  {
    name: 'deleteHermesConfig',
    call: () => api.deleteHermesConfig('p1'),
    url: '/api/projects/p1/hermes-config',
    method: 'DELETE',
    error: 'Failed to delete Hermes config',
  },
  {
    name: 'getJiraConfig',
    call: () => api.getJiraConfig('p1'),
    url: '/api/projects/p1/jira-config',
    error: 'Failed to load Jira config',
  },
  {
    name: 'saveJiraConfig',
    call: () => api.saveJiraConfig('p1', payload),
    url: '/api/projects/p1/jira-config',
    method: 'POST',
    body: payload,
    error: 'Failed to save Jira config',
  },
  {
    name: 'deleteJiraConfig',
    call: () => api.deleteJiraConfig('p1'),
    url: '/api/projects/p1/jira-config',
    method: 'DELETE',
    error: 'Failed to delete Jira config',
  },
  {
    name: 'getDocumentFolderConfig',
    call: () => api.getDocumentFolderConfig('p1'),
    url: '/api/projects/p1/document-folder-config',
    error: 'Failed to load document folder config',
  },
  {
    name: 'saveDocumentFolderConfig',
    call: () => api.saveDocumentFolderConfig('p1', payload),
    url: '/api/projects/p1/document-folder-config',
    method: 'POST',
    body: payload,
    error: 'Failed to save document folder config',
  },
  {
    name: 'deleteDocumentFolderConfig',
    call: () => api.deleteDocumentFolderConfig('p1'),
    url: '/api/projects/p1/document-folder-config',
    method: 'DELETE',
    error: 'Failed to delete document folder config',
  },
  {
    name: 'getApprovalGateContext',
    call: () => api.getApprovalGateContext('r1'),
    url: '/api/agent-runs/r1/approval-gate',
    error: 'Failed to load the approval gate',
  },
  {
    name: 'submitApprovalDecision',
    call: () => api.submitApprovalDecision('r1', payload),
    url: '/api/agent-runs/r1/approval-gate/decision',
    method: 'POST',
    body: payload,
    error: 'Failed to submit the approval decision',
  },
  {
    name: 'listApprovalDecisions',
    call: () => api.listApprovalDecisions('r1'),
    url: '/api/agent-runs/r1/approval-decisions',
    error: 'Failed to load approval decisions',
  },
  {
    name: 'getWorkflowDecisionLogs',
    call: () => api.getWorkflowDecisionLogs('w1'),
    url: '/api/workflows/w1/decision-logs',
    error: 'Failed to load decision logs',
  },
  {
    name: 'getWorkflowExecutionDecisionLogs',
    call: () => api.getWorkflowExecutionDecisionLogs('e1'),
    url: '/api/workflow-executions/e1/decision-logs',
    error: 'Failed to load decision logs',
  },
];

describe('workflowApi endpoints', () => {
  beforeEach(() => {
    mockFetch.mockReset();
    vi.stubGlobal('fetch', mockFetch);
    setAuthToken('tok');
  });
  afterEach(() => vi.unstubAllGlobals());

  it.each(cases)('$name sends an authenticated request', async (c) => {
    mockFetch.mockResolvedValueOnce(ok({ id: 'x' }));
    await c.call();
    const [url, init] = mockFetch.mock.calls[0]!;
    expect(url).toBe(c.url);
    expect(init.method).toBe(c.method);
    expect(init.headers.Authorization).toBe('Bearer tok');
    if (c.body === undefined) {
      expect(init.body).toBeUndefined();
    } else {
      expect(init.headers['Content-Type']).toBe('application/json');
      expect(JSON.parse(init.body)).toEqual(c.body);
    }
  });

  it.each(cases)('$name rejects with "$error: <status>" on a failed response', async (c) => {
    mockFetch.mockResolvedValueOnce(fail(500));
    await expect(c.call()).rejects.toThrow(`${c.error}: 500`);
  });

  it('surfaces the server message for endpoints that parse it', async () => {
    mockFetch.mockResolvedValueOnce(fail(400, { message: 'gate misplaced in stage 2' }));
    await expect(api.createWorkflow(payload)).rejects.toThrow('gate misplaced in stage 2');
  });

  describe('decision log filters', () => {
    it('serialises only the set filter fields', async () => {
      mockFetch.mockResolvedValueOnce(ok([]));
      await api.getWorkflowDecisionLogs('w1', {
        from: '2026-01-01',
        to: '2026-02-01',
        agentId: 'a',
        subagentId: 's',
        skillId: 'k',
        mcpToolName: 't',
        result: 'DENY',
      });
      const params = new URL(mockFetch.mock.calls[0]![0], 'http://x').searchParams;
      expect(Object.fromEntries(params)).toEqual({
        from: '2026-01-01',
        to: '2026-02-01',
        agentId: 'a',
        subagentId: 's',
        skillId: 'k',
        mcpToolName: 't',
        result: 'DENY',
      });
    });

    it('omits the query string when the filter is empty', async () => {
      mockFetch.mockResolvedValueOnce(ok([]));
      await api.getWorkflowExecutionDecisionLogs('e1', {});
      expect(mockFetch.mock.calls[0]![0]).toBe('/api/workflow-executions/e1/decision-logs');
    });
  });

  describe('checkOpaHealth', () => {
    beforeEach(() => vi.spyOn(console, 'warn').mockImplementation(() => {}));
    afterEach(() => vi.restoreAllMocks());

    it('returns the reachable flag', async () => {
      mockFetch.mockResolvedValueOnce(ok({ reachable: true }));
      await expect(api.checkOpaHealth()).resolves.toBe(true);
      mockFetch.mockResolvedValueOnce(ok({}));
      await expect(api.checkOpaHealth()).resolves.toBe(false);
    });

    it('returns false on a failed response', async () => {
      mockFetch.mockResolvedValueOnce(fail(503));
      await expect(api.checkOpaHealth()).resolves.toBe(false);
    });

    it('returns false when the request throws', async () => {
      mockFetch.mockRejectedValueOnce(new Error('offline'));
      await expect(api.checkOpaHealth()).resolves.toBe(false);
    });
  });
});
