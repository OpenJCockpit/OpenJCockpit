import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import {
  listWorkflows,
  listAgentSpecs,
  listWorkflowExecutions,
  getWorkflowExecution,
} from './workflowApi';

const mockFetch = vi.fn();

describe('workflowApi', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.restoreAllMocks();
    vi.stubGlobal('fetch', mockFetch);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  describe('listWorkflows', () => {
    it('rejects with the server message on a non-ok fetch response with a JSON message body', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 500,
        json: () => Promise.resolve({ message: 'Workflow storage is unavailable' }),
      });

      await expect(listWorkflows()).rejects.toThrow('Workflow storage is unavailable');
    });

    it('rejects with a status-based fallback message on a non-ok fetch response with no JSON body', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 500,
        json: () => Promise.reject(new Error('not json')),
      });

      await expect(listWorkflows()).rejects.toThrow('Failed to load workflows: 500');
    });

    it('rejects on a thrown fetch error', async () => {
      mockFetch.mockRejectedValueOnce(new Error('network error'));

      await expect(listWorkflows()).rejects.toThrow('network error');
    });

    it('resolves to the parsed JSON body on a successful ok fetch response', async () => {
      const mockData = [
        { id: '1', name: 'workflow-1' },
        { id: '2', name: 'workflow-2' },
      ];

      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockData),
      });

      const result = await listWorkflows();

      expect(result).toEqual(mockData);
    });
  });

  describe('listAgentSpecs', () => {
    it('resolves to an empty array on a non-ok fetch response', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 503,
      });

      const result = await listAgentSpecs();

      expect(result).toEqual([]);
    });

    it('resolves to an empty array on a thrown fetch error', async () => {
      mockFetch.mockRejectedValueOnce(new Error('timeout'));

      const result = await listAgentSpecs();

      expect(result).toEqual([]);
    });

    it('resolves to the parsed JSON body on a successful ok fetch response', async () => {
      const mockData = [
        { id: 'spec-1', name: 'agent-spec-1' },
        { id: 'spec-2', name: 'agent-spec-2' },
      ];

      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockData),
      });

      const result = await listAgentSpecs();

      expect(result).toEqual(mockData);
    });
  });

  describe('listWorkflowExecutions', () => {
    it('resolves to the parsed page on a successful ok fetch response with limit/offset', async () => {
      const mockPage = {
        items: [
          {
            runId: 'run-1',
            workflowId: 'wf-1',
            status: 'COMPLETED',
            startedAt: '2024-01-01T00:00:00.000Z',
            completedAt: '2024-01-01T00:05:00.000Z',
            durationMillis: 300000,
            startedBy: 'alice',
          },
        ],
        limit: 20,
        offset: 0,
        total: 1,
        hasMore: false,
      };
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockPage),
      });

      const result = await listWorkflowExecutions('wf-1', { limit: 20, offset: 0 });

      expect(result).toEqual(mockPage);
      const calledUrl = mockFetch.mock.calls[0][0] as string;
      expect(calledUrl).toContain('/api/workflows/wf-1/executions');
      expect(calledUrl).toContain('limit=20');
      expect(calledUrl).toContain('offset=0');
    });

    it('omits limit/offset from the query string when no params are given', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ items: [], limit: 20, offset: 0, total: 0, hasMore: false }),
      });

      await listWorkflowExecutions('wf-1');

      const calledUrl = mockFetch.mock.calls[0][0] as string;
      expect(calledUrl).toContain('/api/workflows/wf-1/executions');
    });

    it('throws on a non-ok fetch response instead of resolving to an empty page', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 500,
        json: () => Promise.reject(new Error('not json')),
      });

      await expect(listWorkflowExecutions('wf-1')).rejects.toThrow();
    });
  });

  describe('getWorkflowExecution', () => {
    it('resolves to the parsed run on a successful ok fetch response', async () => {
      const mockRun = {
        runId: 'run-1',
        customerId: 'cust-1',
        specFile: 'spec.md',
        repositoryUrl: 'https://example.com/repo.git',
        status: 'COMPLETED',
        startedAt: '2024-01-01T00:00:00.000Z',
        events: [],
        generatedArtifacts: [],
        workflowId: 'wf-1',
        startedBy: 'alice',
        completedAt: '2024-01-01T00:05:00.000Z',
      };
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockRun),
      });

      const result = await getWorkflowExecution('wf-1', 'run-1');

      expect(result).toEqual(mockRun);
      const calledUrl = mockFetch.mock.calls[0][0] as string;
      expect(calledUrl).toContain('/api/workflows/wf-1/executions/run-1');
    });

    it('throws on a non-ok fetch response', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 404,
        json: () => Promise.reject(new Error('not json')),
      });

      await expect(getWorkflowExecution('wf-1', 'run-1')).rejects.toMatchObject({ status: 404 });
    });
  });
});
