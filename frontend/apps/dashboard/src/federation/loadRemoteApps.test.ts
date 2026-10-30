import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { HUB_CONTRACT_VERSION } from '@openjcockpit/hub-contract';

const loadRemote = vi.hoisted(() => vi.fn());
vi.mock('@module-federation/enhanced/runtime', () => ({ loadRemote }));

import { loadRemoteApps } from './loadRemoteApps';

const app = (id: string) => ({ id, route: `/${id}`, loadRoutes: () => Promise.resolve([]) });

describe('loadRemoteApps', () => {
  beforeEach(() => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
  });
  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
    loadRemote.mockReset();
  });

  it('loads each remote by its exposed module id', async () => {
    loadRemote.mockImplementation((id: string) => Promise.resolve({ app: app(id.split('/')[0]!) }));
    const result = await loadRemoteApps(['a', 'b']);
    expect(loadRemote).toHaveBeenCalledWith('a/app');
    expect(result.apps.map((a) => a.id)).toEqual(['a', 'b']);
    expect(result.failed).toEqual([]);
  });

  it('skips and reports a rejecting remote, a bad export and a missing module', async () => {
    const load = vi.fn((name: string) => {
      if (name === 'down') return Promise.reject(new Error('offline'));
      if (name === 'junk') return Promise.resolve({ app: { id: 1 } });
      if (name === 'empty') return Promise.resolve(null);
      return Promise.resolve({ app: app(name) });
    });
    const result = await loadRemoteApps(['down', 'junk', 'empty', 'ok'], load);
    expect(result.apps.map((a) => a.id)).toEqual(['ok']);
    expect(result.failed).toEqual(['down', 'junk', 'empty']);
    expect(console.error).toHaveBeenCalledTimes(3);
  });

  it('accepts a remote at or below the host contract version, rejects a higher one', async () => {
    const load = vi.fn((name: string) =>
      Promise.resolve({
        app: app(name),
        contractVersion: name === 'new' ? HUB_CONTRACT_VERSION + 1 : HUB_CONTRACT_VERSION,
      }),
    );
    const result = await loadRemoteApps(['same', 'new'], load);
    expect(result.apps.map((a) => a.id)).toEqual(['same']);
    expect(result.failed).toEqual(['new']);
  });

  it('rejects a non-numeric contract version', async () => {
    const result = await loadRemoteApps(['x'], () =>
      Promise.resolve({ app: app('x'), contractVersion: 'one' }),
    );
    expect(result.failed).toEqual(['x']);
  });

  it('times out a hung remote without blocking the others', async () => {
    vi.useFakeTimers();
    const load = (name: string) =>
      name === 'hung' ? new Promise<never>(() => undefined) : Promise.resolve({ app: app(name) });
    const pending = loadRemoteApps(['hung', 'ok'], load, 50);
    await vi.advanceTimersByTimeAsync(60);
    const result = await pending;
    expect(result.apps.map((a) => a.id)).toEqual(['ok']);
    expect(result.failed).toEqual(['hung']);
  });
});
