import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { apiRequest, authHeaders, isAbortError, setAuthToken } from './http';
import { getSpecQueue } from '../spec-queue/specQueueApi';
import { listWorkflows } from '../workflow/workflowApi';

const mockFetch = vi.fn();

describe('auth token', () => {
  beforeEach(() => {
    mockFetch.mockReset().mockResolvedValue({ ok: true, json: () => Promise.resolve([]) });
    vi.stubGlobal('fetch', mockFetch);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('adds a bearer Authorization header once a token is set', () => {
    setAuthToken('secret-token');

    expect(authHeaders()).toEqual({ Authorization: 'Bearer secret-token' });
  });

  it('is shared by every backend client in the library', async () => {
    setAuthToken('shared-token');

    await listWorkflows();
    await getSpecQueue('project-1');

    for (const [, init] of mockFetch.mock.calls) {
      expect(init.headers).toMatchObject({
        Authorization: 'Bearer shared-token',
      });
    }
    expect(mockFetch).toHaveBeenCalledTimes(2);
  });
});

describe('apiRequest', () => {
  beforeEach(() => {
    mockFetch.mockReset();
    vi.stubGlobal('fetch', mockFetch);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('forwards the abort signal to fetch', async () => {
    mockFetch.mockResolvedValue({ ok: true, json: () => Promise.resolve([]) });
    const controller = new AbortController();

    await apiRequest('/api/projects', { signal: controller.signal, errorMessage: 'Failed' });

    expect(mockFetch.mock.calls[0][1].signal).toBe(controller.signal);
  });

  it('rejects instead of returning an empty result on a failed response', async () => {
    mockFetch.mockResolvedValue({
      ok: false,
      status: 503,
      json: () => Promise.reject(new Error()),
    });

    await expect(
      apiRequest('/api/projects', { errorMessage: 'Failed to load projects' }),
    ).rejects.toThrow('Failed to load projects: 503');
  });

  it('recognises an aborted request', async () => {
    mockFetch.mockRejectedValue(new DOMException('aborted', 'AbortError'));

    const error = await apiRequest('/x', { errorMessage: 'Failed' }).catch((e) => e);

    expect(isAbortError(error)).toBe(true);
    expect(isAbortError(new Error('boom'))).toBe(false);
  });
});
