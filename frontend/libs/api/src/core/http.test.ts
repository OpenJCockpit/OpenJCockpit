import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  apiRequest,
  apiRequestVoid,
  authHeaders,
  isAbortError,
  setAuthToken,
  setErrorTranslator,
  setSessionRefresher,
} from './http';
import { getSpecQueue } from '../spec-queue/specQueueApi';
import { deleteWorkflow, listWorkflows } from '../workflow/workflowApi';

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

    expect(mockFetch.mock.calls[0]![1].signal).toBe(controller.signal);
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

  it('sends a JSON body with a Content-Type header', async () => {
    mockFetch.mockResolvedValue({ ok: true, json: () => Promise.resolve({}) });

    await apiRequest('/api/projects', {
      method: 'POST',
      body: { name: 'x' },
      errorMessage: 'Failed',
    });

    const init = mockFetch.mock.calls[0]![1];
    expect(init.headers).toMatchObject({ 'Content-Type': 'application/json' });
    expect(init.body).toBe('{"name":"x"}');
  });

  it('omits Content-Type and body when there is no payload', async () => {
    mockFetch.mockResolvedValue({ ok: true, json: () => Promise.resolve({}) });

    await apiRequest('/api/projects', { errorMessage: 'Failed' });

    const init = mockFetch.mock.calls[0]![1];
    expect(init.headers).not.toHaveProperty('Content-Type');
    expect(init.body).toBeUndefined();
  });

  it('recognises an aborted request', async () => {
    mockFetch.mockRejectedValue(new DOMException('aborted', 'AbortError'));

    const error = await apiRequest('/x', { errorMessage: 'Failed' }).catch((e) => e);

    expect(isAbortError(error)).toBe(true);
    expect(isAbortError(new Error('boom'))).toBe(false);
  });
});

describe('401 handling', () => {
  const ok = { ok: true, status: 200, json: () => Promise.resolve({ done: true }) };
  const unauthorized = { ok: false, status: 401, json: () => Promise.reject(new Error()) };

  beforeEach(() => {
    mockFetch.mockReset();
    vi.stubGlobal('fetch', mockFetch);
    setAuthToken('stale');
  });

  afterEach(() => {
    setSessionRefresher(null);
    vi.unstubAllGlobals();
  });

  it('refreshes the session once and retries with the new token', async () => {
    const refresher = vi.fn().mockResolvedValue('fresh');
    setSessionRefresher(refresher);
    mockFetch.mockResolvedValueOnce(unauthorized).mockResolvedValueOnce(ok);

    await expect(apiRequest('/api/x', { errorMessage: 'Failed' })).resolves.toEqual({ done: true });

    expect(refresher).toHaveBeenCalledTimes(1);
    expect(mockFetch).toHaveBeenCalledTimes(2);
    expect(mockFetch.mock.calls[1]![1].headers).toMatchObject({ Authorization: 'Bearer fresh' });
  });

  it('throws the 401 when the retry is rejected too, without refreshing again', async () => {
    const refresher = vi.fn().mockResolvedValue('fresh');
    setSessionRefresher(refresher);
    mockFetch.mockResolvedValue(unauthorized);

    await expect(apiRequest('/api/x', { errorMessage: 'Failed' })).rejects.toMatchObject({
      status: 401,
    });
    expect(refresher).toHaveBeenCalledTimes(1);
    expect(mockFetch).toHaveBeenCalledTimes(2);
  });

  it('rejects without retrying when the refresh fails', async () => {
    setSessionRefresher(vi.fn().mockRejectedValue(new Error('expired')));
    mockFetch.mockResolvedValue(unauthorized);

    await expect(apiRequest('/api/x', { errorMessage: 'Failed' })).rejects.toThrow('expired');
    expect(mockFetch).toHaveBeenCalledTimes(1);
  });

  it('shares one refresh between concurrent 401s', async () => {
    const refresher = vi.fn().mockResolvedValue('fresh');
    setSessionRefresher(refresher);
    mockFetch.mockImplementation((_url, init) =>
      Promise.resolve(init.headers.Authorization === 'Bearer fresh' ? ok : unauthorized),
    );

    await Promise.all([
      apiRequest('/api/a', { errorMessage: 'Failed' }),
      apiRequest('/api/b', { errorMessage: 'Failed' }),
    ]);

    expect(refresher).toHaveBeenCalledTimes(1);
  });

  it('throws the 401 as before when no refresher is registered', async () => {
    mockFetch.mockResolvedValue(unauthorized);

    await expect(apiRequest('/api/x', { errorMessage: 'Failed' })).rejects.toMatchObject({
      status: 401,
    });
    expect(mockFetch).toHaveBeenCalledTimes(1);
  });

  it('also renews the session for write clients that return no body', async () => {
    setSessionRefresher(vi.fn().mockResolvedValue('fresh'));
    mockFetch.mockResolvedValueOnce(unauthorized).mockResolvedValueOnce({ ok: true, status: 204 });

    await expect(deleteWorkflow('wf-1')).resolves.toBeUndefined();

    expect(mockFetch).toHaveBeenCalledTimes(2);
    expect(mockFetch.mock.calls[1]![1].headers).toMatchObject({ Authorization: 'Bearer fresh' });
  });
});

describe('apiRequestVoid', () => {
  beforeEach(() => {
    mockFetch.mockReset();
    vi.stubGlobal('fetch', mockFetch);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('resolves without reading the response body', async () => {
    const json = vi.fn();
    mockFetch.mockResolvedValue({ ok: true, status: 204, json });

    await expect(
      apiRequestVoid('/api/x', { method: 'DELETE', errorMessage: 'Failed' }),
    ).resolves.toBeUndefined();

    expect(json).not.toHaveBeenCalled();
  });

  it('rejects with the status on a failed response', async () => {
    mockFetch.mockResolvedValue({
      ok: false,
      status: 500,
      json: () => Promise.reject(new Error()),
    });

    await expect(
      apiRequestVoid('/api/x', { errorMessage: 'Failed to delete' }),
    ).rejects.toMatchObject({
      message: 'Failed to delete: 500',
      status: 500,
    });
  });
});

describe('server error codes', () => {
  const failWith = (body: unknown) =>
    mockFetch.mockResolvedValue({ ok: false, status: 409, json: () => Promise.resolve(body) });

  beforeEach(() => {
    mockFetch.mockReset();
    vi.stubGlobal('fetch', mockFetch);
  });

  afterEach(() => {
    setErrorTranslator(null);
    vi.unstubAllGlobals();
  });

  it('shows the translated message for a known code and keeps the code on the error', async () => {
    setErrorTranslator((code) => (code === 'NAME_CONFLICT' ? 'Naam bestaat al' : undefined));
    failWith({ code: 'NAME_CONFLICT', message: 'A connection with this name already exists' });

    await expect(apiRequestVoid('/api/x', { errorMessage: 'Failed' })).rejects.toMatchObject({
      message: 'Naam bestaat al',
      code: 'NAME_CONFLICT',
      status: 409,
    });
  });

  it('keeps the server message for a code without a translation', async () => {
    setErrorTranslator(() => undefined);
    failWith({ code: 'VALIDATION_ERROR', message: 'name must not be blank' });

    await expect(apiRequestVoid('/api/x', { errorMessage: 'Failed' })).rejects.toMatchObject({
      message: 'name must not be blank',
      code: 'VALIDATION_ERROR',
    });
  });

  it('keeps the server message when no translator is registered', async () => {
    failWith({ code: 'NAME_CONFLICT', message: 'A connection with this name already exists' });

    await expect(apiRequestVoid('/api/x', { errorMessage: 'Failed' })).rejects.toMatchObject({
      message: 'A connection with this name already exists',
    });
  });
});
