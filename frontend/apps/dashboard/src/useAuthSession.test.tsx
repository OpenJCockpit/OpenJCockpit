import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Mock } from 'vitest';
import * as api from '@openjcockpit/api';
import * as auth from '@openjcockpit/auth';
import { useAuthSession } from './useAuthSession';

vi.mock('@openjcockpit/api', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@openjcockpit/api')>()),
  setAuthToken: vi.fn(),
  setSessionRefresher: vi.fn(),
}));
vi.mock('@openjcockpit/auth', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@openjcockpit/auth')>()),
  getKeycloak: vi.fn(),
  getUserFromToken: vi.fn(),
}));

interface FakeKeycloak {
  token?: string;
  tokenParsed?: object;
  init: ReturnType<typeof vi.fn>;
  login: ReturnType<typeof vi.fn>;
  updateToken: Mock<(minValidity: number) => Promise<boolean>>;
}

function fakeKeycloak(overrides: Partial<FakeKeycloak> = {}): FakeKeycloak {
  return {
    token: 'tok-1',
    tokenParsed: { sub: 'u1' },
    init: vi.fn().mockResolvedValue(true),
    login: vi.fn(),
    updateToken: vi.fn().mockResolvedValue(false),
    ...overrides,
  };
}

function useKeycloak(keycloak: FakeKeycloak) {
  vi.mocked(auth.getKeycloak).mockReturnValue(
    keycloak as unknown as ReturnType<typeof auth.getKeycloak>,
  );
}

async function mount() {
  const hook = renderHook(() => useAuthSession());
  await act(async () => {});
  return hook;
}

describe('useAuthSession', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.useFakeTimers();
    vi.mocked(auth.getUserFromToken).mockReturnValue({ name: 'Dana' } as never);
  });
  afterEach(() => vi.useRealTimers());

  it('initialises Keycloak with PKCE and login-required', async () => {
    const keycloak = fakeKeycloak();
    useKeycloak(keycloak);
    await mount();
    expect(keycloak.init).toHaveBeenCalledWith({
      onLoad: 'login-required',
      pkceMethod: 'S256',
      checkLoginIframe: false,
    });
  });

  it('is unauthenticated until init resolves, then exposes the user and sets the token', async () => {
    useKeycloak(fakeKeycloak());
    const { result } = renderHook(() => useAuthSession());
    expect(result.current).toEqual({ authenticated: false, user: null });
    await act(async () => {});
    expect(result.current).toEqual({ authenticated: true, user: { name: 'Dana' } });
    expect(api.setAuthToken).toHaveBeenCalledWith('tok-1');
    expect(auth.getUserFromToken).toHaveBeenCalledWith({ sub: 'u1' });
  });

  it('shares a single init across re-runs of the effect', async () => {
    const keycloak = fakeKeycloak();
    useKeycloak(keycloak);
    const first = await mount();
    first.unmount();
    await mount();
    expect(keycloak.init).toHaveBeenCalledTimes(1);
  });

  it('redirects to login when init reports not authenticated', async () => {
    const keycloak = fakeKeycloak({ init: vi.fn().mockResolvedValue(false) });
    useKeycloak(keycloak);
    const { result } = await mount();
    expect(keycloak.login).toHaveBeenCalledTimes(1);
    expect(result.current.authenticated).toBe(false);
  });

  it('redirects to login when there is no token', async () => {
    const keycloak = fakeKeycloak({ token: undefined });
    useKeycloak(keycloak);
    await mount();
    expect(keycloak.login).toHaveBeenCalledTimes(1);
    expect(api.setAuthToken).not.toHaveBeenCalled();
  });

  it('logs a failed init without trying to log in', async () => {
    const error = vi.spyOn(console, 'error').mockImplementation(() => {});
    const keycloak = fakeKeycloak({ init: vi.fn().mockRejectedValue(new Error('no adapter')) });
    useKeycloak(keycloak);
    const { result } = await mount();
    expect(error).toHaveBeenCalledWith('Keycloak initialisation failed', expect.any(Error));
    expect(keycloak.login).not.toHaveBeenCalled();
    expect(result.current.authenticated).toBe(false);
    error.mockRestore();
  });

  it('does nothing when unmounted before init resolves', async () => {
    let resolve!: (ok: boolean) => void;
    const keycloak = fakeKeycloak({
      init: vi.fn(() => new Promise<boolean>((r) => (resolve = r))),
    });
    useKeycloak(keycloak);
    const { unmount } = renderHook(() => useAuthSession());
    unmount();
    await act(async () => {
      resolve(true);
    });
    expect(api.setAuthToken).not.toHaveBeenCalled();
  });

  it('refreshes the token every 30 seconds and forwards a new one', async () => {
    const keycloak = fakeKeycloak();
    keycloak.updateToken.mockImplementation(() => {
      keycloak.token = 'tok-2';
      return Promise.resolve(true);
    });
    useKeycloak(keycloak);
    await mount();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(30_000);
    });
    expect(keycloak.updateToken).toHaveBeenCalledWith(60);
    expect(api.setAuthToken).toHaveBeenLastCalledWith('tok-2');
  });

  it('keeps the current token when the refresh did not renew it', async () => {
    const keycloak = fakeKeycloak();
    useKeycloak(keycloak);
    await mount();
    vi.mocked(api.setAuthToken).mockClear();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(30_000);
    });
    expect(api.setAuthToken).not.toHaveBeenCalled();
  });

  it('logs in again when refreshing fails', async () => {
    const keycloak = fakeKeycloak();
    keycloak.updateToken.mockRejectedValue(new Error('expired'));
    useKeycloak(keycloak);
    await mount();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(30_000);
    });
    expect(keycloak.login).toHaveBeenCalledTimes(1);
  });

  it('stops refreshing after unmount', async () => {
    const keycloak = fakeKeycloak();
    useKeycloak(keycloak);
    const { unmount } = await mount();
    unmount();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(120_000);
    });
    expect(keycloak.updateToken).not.toHaveBeenCalled();
  });

  describe('session refresher for rejected API calls', () => {
    async function mountWithRefresher(keycloak: FakeKeycloak) {
      vi.mocked(auth.getKeycloak).mockReturnValue(
        keycloak as unknown as ReturnType<typeof auth.getKeycloak>,
      );
      const hook = await mount();
      const refresher = vi.mocked(api.setSessionRefresher).mock.calls[0]![0]!;
      return { hook, refresher };
    }

    it('forces a token refresh and returns the new token', async () => {
      const keycloak = fakeKeycloak();
      keycloak.updateToken.mockImplementation(() => {
        keycloak.token = 'tok-2';
        return Promise.resolve(true);
      });
      const { refresher } = await mountWithRefresher(keycloak);
      await expect(refresher()).resolves.toBe('tok-2');
      expect(keycloak.updateToken).toHaveBeenCalledWith(-1);
      expect(keycloak.login).not.toHaveBeenCalled();
    });

    it('redirects to login and rejects when the refresh fails', async () => {
      const keycloak = fakeKeycloak();
      keycloak.updateToken.mockRejectedValue(new Error('expired'));
      const { refresher } = await mountWithRefresher(keycloak);
      await expect(refresher()).rejects.toThrow('expired');
      expect(keycloak.login).toHaveBeenCalledTimes(1);
    });

    it('redirects to login when no token is left after the refresh', async () => {
      const keycloak = fakeKeycloak();
      keycloak.updateToken.mockImplementation(() => {
        keycloak.token = undefined;
        return Promise.resolve(true);
      });
      const { refresher } = await mountWithRefresher(keycloak);
      await expect(refresher()).rejects.toThrow('No token after refresh');
      expect(keycloak.login).toHaveBeenCalledTimes(1);
    });

    it('unregisters the refresher on unmount', async () => {
      const { hook } = await mountWithRefresher(fakeKeycloak());
      hook.unmount();
      expect(api.setSessionRefresher).toHaveBeenLastCalledWith(null);
    });
  });
});
