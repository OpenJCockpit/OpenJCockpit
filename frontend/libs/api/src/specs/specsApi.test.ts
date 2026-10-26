import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, setAuthToken } from '../core/http';
import { getSpecInitStatus, initSpecFolder, loadProjectSpecs, saveSpecFile } from './specsApi';

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

describe('specsApi', () => {
  beforeEach(() => {
    mockFetch.mockReset();
    vi.stubGlobal('fetch', mockFetch);
    setAuthToken('tok');
  });
  afterEach(() => vi.unstubAllGlobals());

  it('loadProjectSpecs GETs the spec files and forwards the abort signal', async () => {
    mockFetch.mockResolvedValueOnce(ok([{ name: 'a.md' }]));
    const { signal } = new AbortController();
    await expect(loadProjectSpecs('p1', signal)).resolves.toEqual([{ name: 'a.md' }]);
    const [url, init] = mockFetch.mock.calls[0]!;
    expect(url).toBe('/api/projects/p1/spec-files');
    expect(init.signal).toBe(signal);
    expect(init.headers.Authorization).toBe('Bearer tok');
  });

  it('loadProjectSpecs rejects with a prefixed error message', async () => {
    mockFetch.mockResolvedValueOnce(fail(500));
    await expect(loadProjectSpecs('p1')).rejects.toThrow('Failed to load spec files: 500');
  });

  it('getSpecInitStatus GETs the status', async () => {
    mockFetch.mockResolvedValueOnce(ok(null));
    await expect(getSpecInitStatus('p1')).resolves.toBeNull();
    expect(mockFetch.mock.calls[0]![0]).toBe('/api/projects/p1/spec-init/status');
  });

  it('initSpecFolder POSTs and returns the result', async () => {
    mockFetch.mockResolvedValueOnce(ok({ created: true }));
    await expect(initSpecFolder('p1')).resolves.toEqual({ created: true });
    const [url, init] = mockFetch.mock.calls[0]!;
    expect(url).toBe('/api/projects/p1/spec-init');
    expect(init.method).toBe('POST');
  });

  it('initSpecFolder throws an ApiError with the server message', async () => {
    mockFetch.mockResolvedValueOnce(fail(409, { message: 'already exists' }));
    const error = await initSpecFolder('p1').catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ message: 'already exists', status: 409 });
  });

  it('saveSpecFile PUTs the file name and content as JSON', async () => {
    mockFetch.mockResolvedValueOnce(ok({ saved: true }));
    await saveSpecFile('p1', 'a.md', '# hi');
    const [url, init] = mockFetch.mock.calls[0]!;
    expect(url).toBe('/api/projects/p1/spec-files');
    expect(init.method).toBe('PUT');
    expect(init.headers['Content-Type']).toBe('application/json');
    expect(JSON.parse(init.body)).toEqual({ fileName: 'a.md', content: '# hi' });
  });

  it('saveSpecFile throws an ApiError with the generic fallback', async () => {
    mockFetch.mockResolvedValueOnce(fail(500));
    await expect(saveSpecFile('p1', 'a.md', 'x')).rejects.toMatchObject({
      message: 'Failed to save spec file: 500',
      status: 500,
    });
  });
});
