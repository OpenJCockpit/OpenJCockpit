import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { createHubHttp, HubHttpProvider, useHubHttp } from './http';

const tokenOf = (): Promise<string | undefined> => Promise.resolve('tok');

function setup(getToken: () => Promise<string | undefined> = tokenOf) {
  const fetchImpl = vi.fn<typeof fetch>(() => Promise.resolve(new Response('ok')));
  const http = createHubHttp({ getToken }, 'https://api.test/v1', fetchImpl);
  return { fetchImpl, http };
}

function sentAuth(fetchImpl: ReturnType<typeof setup>['fetchImpl']) {
  const init = fetchImpl.mock.calls[0]?.[1];
  return new Headers(init?.headers).get('Authorization');
}

describe('createHubHttp', () => {
  it('adds the bearer token under the api base', async () => {
    const { fetchImpl, http } = setup();
    await http.fetch('https://api.test/v1/projects', { headers: { Accept: 'x' } });
    expect(sentAuth(fetchImpl)).toBe('Bearer tok');
  });

  it('never sends the token to another origin or path', async () => {
    const { fetchImpl, http } = setup();
    await http.fetch('https://evil.test/v1/projects');
    await http.fetch('https://api.test/v10/projects');
    expect(fetchImpl.mock.calls.map((c) => c[1]?.headers)).toEqual([undefined, undefined]);
  });

  it('sends the request unauthenticated when there is no token', async () => {
    const { fetchImpl, http } = setup(() => Promise.resolve(undefined));
    await http.fetch('https://api.test/v1/projects');
    expect(sentAuth(fetchImpl)).toBeNull();
  });
});

describe('createHubHttp default fetch', () => {
  it('falls back to the global fetch', async () => {
    const globalFetch = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('ok'));
    await createHubHttp({ getToken: tokenOf }, 'https://api.test/v1').fetch('https://x.test/a');
    expect(globalFetch).toHaveBeenCalledOnce();
  });
});

describe('useHubHttp', () => {
  function Probe() {
    useHubHttp();
    return <p>ok</p>;
  }

  it('returns the provided client', () => {
    render(
      <HubHttpProvider http={setup().http}>
        <Probe />
      </HubHttpProvider>,
    );
    expect(screen.getByText('ok')).toBeInTheDocument();
  });

  it('throws without a provider', () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    expect(() => render(<Probe />)).toThrow('HubHttpProvider');
  });
});
