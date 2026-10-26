import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setAuthToken } from '../core/http';
import { checkWorkflowPreflight, getAgentRun, loadWorkspace, stopAgentRun } from './workspaceApi';

const mockFetch = vi.fn();

function ok(body?: unknown) {
  return { ok: true, status: 200, json: () => Promise.resolve(body) };
}
function fail(status: number) {
  return { ok: false, status, json: () => Promise.reject(new Error('not json')) };
}

describe('workspaceApi', () => {
  beforeEach(() => {
    mockFetch.mockReset();
    vi.stubGlobal('fetch', mockFetch);
    setAuthToken('tok');
  });
  afterEach(() => vi.unstubAllGlobals());

  it('loadWorkspace GETs the customer workspace', async () => {
    mockFetch.mockResolvedValueOnce(ok({ id: 'c1' }));
    await expect(loadWorkspace('c1')).resolves.toEqual({ id: 'c1' });
    expect(mockFetch.mock.calls[0]![0]).toBe('/api/workspaces/c1');
  });

  it('loadWorkspace rejects with a prefixed error', async () => {
    mockFetch.mockResolvedValueOnce(fail(404));
    await expect(loadWorkspace('c1')).rejects.toThrow('Failed to load workspace: 404');
  });

  it('checkWorkflowPreflight POSTs with the project id in the query', async () => {
    mockFetch.mockResolvedValueOnce(ok({ passed: true }));
    await expect(checkWorkflowPreflight('p1')).resolves.toEqual({ passed: true });
    const [url, init] = mockFetch.mock.calls[0]!;
    expect(url).toBe('/api/agentic-workflows/preflight?projectId=p1');
    expect(init.method).toBe('POST');
    expect(init.headers.Authorization).toBe('Bearer tok');
  });

  it('getAgentRun GETs the run', async () => {
    mockFetch.mockResolvedValueOnce(ok({ id: 'r1' }));
    await expect(getAgentRun('r1')).resolves.toEqual({ id: 'r1' });
    expect(mockFetch.mock.calls[0]![0]).toBe('/api/agent-runs/r1');
  });

  it('stopAgentRun DELETEs the run', async () => {
    mockFetch.mockResolvedValueOnce(ok());
    await stopAgentRun('r1');
    const [url, init] = mockFetch.mock.calls[0]!;
    expect(url).toBe('/api/agent-runs/r1');
    expect(init.method).toBe('DELETE');
  });

  it.each([
    [
      'checkWorkflowPreflight',
      () => checkWorkflowPreflight('p1'),
      'Failed to run pre-flight checks: 500',
    ],
    ['getAgentRun', () => getAgentRun('r1'), 'Failed to get agent run: 500'],
    ['stopAgentRun', () => stopAgentRun('r1'), 'Failed to stop agent run: 500'],
  ])('%s rejects on a failed response', async (_name, call, message) => {
    mockFetch.mockResolvedValueOnce(fail(500));
    await expect(call()).rejects.toThrow(message);
  });
});
