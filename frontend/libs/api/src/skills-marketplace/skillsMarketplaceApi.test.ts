import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, setAuthToken } from '../core/http';
import {
  createSkillsMarketplace,
  deleteSkillsMarketplace,
  listSkillsMarketplaces,
  updateSkillsMarketplace,
} from './skillsMarketplaceApi';
import type { SkillsMarketplaceConnectionRequest } from '../types/types';

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

const req = { name: 'm' } as unknown as SkillsMarketplaceConnectionRequest;

describe('skillsMarketplaceApi', () => {
  beforeEach(() => {
    mockFetch.mockReset();
    vi.stubGlobal('fetch', mockFetch);
    setAuthToken('tok');
  });
  afterEach(() => vi.unstubAllGlobals());

  it('listSkillsMarketplaces GETs with auth', async () => {
    mockFetch.mockResolvedValueOnce(ok([{ id: 'm1' }]));
    await expect(listSkillsMarketplaces()).resolves.toEqual([{ id: 'm1' }]);
    const [url, init] = mockFetch.mock.calls[0]!;
    expect(url).toBe('/api/skills-marketplaces');
    expect(init.headers.Authorization).toBe('Bearer tok');
  });

  it('createSkillsMarketplace POSTs JSON', async () => {
    mockFetch.mockResolvedValueOnce(ok({ id: 'm1' }));
    await expect(createSkillsMarketplace(req)).resolves.toEqual({ id: 'm1' });
    const [url, init] = mockFetch.mock.calls[0]!;
    expect(url).toBe('/api/skills-marketplaces');
    expect(init.method).toBe('POST');
    expect(JSON.parse(init.body)).toEqual(req);
  });

  it('updateSkillsMarketplace PUTs JSON to the id', async () => {
    mockFetch.mockResolvedValueOnce(ok({ id: 'm1' }));
    await updateSkillsMarketplace('m1', req);
    const [url, init] = mockFetch.mock.calls[0]!;
    expect(url).toBe('/api/skills-marketplaces/m1');
    expect(init.method).toBe('PUT');
    expect(JSON.parse(init.body)).toEqual(req);
  });

  it('deleteSkillsMarketplace DELETEs the id', async () => {
    mockFetch.mockResolvedValueOnce(ok());
    await deleteSkillsMarketplace('m1');
    const [url, init] = mockFetch.mock.calls[0]!;
    expect(url).toBe('/api/skills-marketplaces/m1');
    expect(init.method).toBe('DELETE');
  });

  it.each([
    ['listSkillsMarketplaces', () => listSkillsMarketplaces(), 'Failed to load marketplaces'],
    ['createSkillsMarketplace', () => createSkillsMarketplace(req), 'Failed to create marketplace'],
    [
      'updateSkillsMarketplace',
      () => updateSkillsMarketplace('m1', req),
      'Failed to update marketplace',
    ],
    [
      'deleteSkillsMarketplace',
      () => deleteSkillsMarketplace('m1'),
      'Failed to delete marketplace',
    ],
  ])('%s throws an ApiError with a fallback message', async (_name, call, message) => {
    mockFetch.mockResolvedValueOnce(fail(503));
    const error = await call().catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ message: `${message}: 503`, status: 503 });
  });

  it('prefers the server message when present', async () => {
    mockFetch.mockResolvedValueOnce(fail(400, { message: 'duplicate url' }));
    await expect(createSkillsMarketplace(req)).rejects.toMatchObject({
      message: 'duplicate url',
      status: 400,
    });
  });
});
