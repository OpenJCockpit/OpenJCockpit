import { createContext, useContext, type ReactNode } from 'react';
import type { HubSession } from './session';

/** Fetch-compatible client; the host decides which URLs get the bearer token. */
export interface HubHttp {
  fetch(input: string | URL, init?: RequestInit): Promise<Response>;
}

/** Adds `Authorization: Bearer` only to requests under `apiBaseUrl`, never to other origins. */
export function createHubHttp(
  session: Pick<HubSession, 'getToken'>,
  apiBaseUrl: string,
  fetchImpl: typeof fetch = (input, init) => fetch(input, init),
): HubHttp {
  const base = new URL(apiBaseUrl, window.location.href);
  const underBase = (url: URL) =>
    url.origin === base.origin && url.pathname.startsWith(base.pathname.replace(/\/?$/, '/'));
  return {
    async fetch(input, init) {
      const url = new URL(input, window.location.href);
      if (!underBase(url)) return fetchImpl(url, init);
      const token = await session.getToken();
      if (!token) return fetchImpl(url, init);
      const headers = new Headers(init?.headers);
      headers.set('Authorization', `Bearer ${token}`);
      return fetchImpl(url, { ...init, headers });
    },
  };
}

const HubHttpContext = createContext<HubHttp | null>(null);

export function HubHttpProvider({ http, children }: { http: HubHttp; children: ReactNode }) {
  return <HubHttpContext.Provider value={http}>{children}</HubHttpContext.Provider>;
}

export function useHubHttp(): HubHttp {
  const http = useContext(HubHttpContext);
  if (!http) throw new Error('useHubHttp needs a HubHttpProvider above it.');
  return http;
}
