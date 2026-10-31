import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const registerRemotes = vi.hoisted(() => vi.fn());
vi.mock('@module-federation/enhanced/runtime', () => ({ registerRemotes }));

import {
  fetchAllowedOrigins,
  fetchManifest,
  isAllowedOrigin,
  registerManifestRemotes,
} from './manifest';

const json = (body: unknown, ok = true) =>
  Promise.resolve({ ok, status: ok ? 200 : 500, json: () => Promise.resolve(body) });

describe('isAllowedOrigin', () => {
  it('allows the host origin, listed origins and (in dev) localhost', () => {
    expect(isAllowedOrigin(`${window.location.origin}/x`, [], false)).toBe(true);
    expect(isAllowedOrigin('https://cdn.example/x', ['https://cdn.example'], false)).toBe(true);
    expect(isAllowedOrigin('http://localhost:4401/x', [], true)).toBe(true);
  });

  it('rejects everything else, including malformed URLs', () => {
    expect(isAllowedOrigin('http://localhost:4401/x', [], false)).toBe(false);
    expect(isAllowedOrigin('https://evil.example/x', [], true)).toBe(false);
    expect(isAllowedOrigin('http://[bad', [], true)).toBe(false);
  });
});

describe('fetchManifest', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('returns the name to URL map', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => json({ a: 'http://x/mf-manifest.json' })),
    );
    await expect(fetchManifest()).resolves.toEqual({ a: 'http://x/mf-manifest.json' });
  });

  it.each([
    ['a failed response', json({}, false)],
    ['a non-object body', json([])],
    ['a null body', json(null)],
    ['a non-string entry', json({ a: 1 })],
  ])('throws on %s', async (_name, response) => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => response),
    );
    await expect(fetchManifest()).rejects.toThrow();
  });
});

describe('fetchAllowedOrigins', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('prefers the runtime config', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => json({ federation: { allowedOrigins: ['https://a', 1] } })),
    );
    await expect(fetchAllowedOrigins()).resolves.toEqual(['https://a']);
  });

  it.each([
    ['a failed response', () => json({}, false)],
    ['no federation section', () => json({})],
    ['a network error', () => Promise.reject(new Error('offline'))],
  ])('falls back to the build-time list on %s', async (_name, response) => {
    vi.stubGlobal('fetch', vi.fn(response));
    vi.stubEnv('VITE_FEDERATION_ALLOWED_ORIGINS', 'https://b,https://c');
    await expect(fetchAllowedOrigins()).resolves.toEqual(['https://b', 'https://c']);
    vi.unstubAllEnvs();
  });

  it('is empty when nothing is configured', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => json({}, false)),
    );
    await expect(fetchAllowedOrigins()).resolves.toEqual([]);
  });
});

describe('registerManifestRemotes', () => {
  beforeEach(() => {
    registerRemotes.mockReset();
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
  });

  it('registers allowed remotes at runtime and reports rejected ones', () => {
    const result = registerManifestRemotes(
      { ok: 'https://cdn.example/mf-manifest.json', bad: 'https://evil.example/mf-manifest.json' },
      ['https://cdn.example'],
      false,
    );
    expect(result).toEqual({ names: ['ok'], rejected: ['bad'] });
    expect(registerRemotes).toHaveBeenCalledWith([
      { name: 'ok', entry: 'https://cdn.example/mf-manifest.json' },
    ]);
  });

  it('defaults dev from the build mode', () => {
    expect(registerManifestRemotes({ a: 'http://localhost:4401/m.json' }, []).names).toEqual(['a']);
  });
});
