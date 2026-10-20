import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { authHeaders, setAuthToken } from './http';
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
