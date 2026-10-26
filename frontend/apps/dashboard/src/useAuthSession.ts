import { useEffect, useState } from 'react';
import { setAuthToken, setSessionRefresher } from '@openjcockpit/api';
import { getKeycloak, getUserFromToken } from '@openjcockpit/auth';
import type { User } from '@openjcockpit/auth';

const TOKEN_REFRESH_INTERVAL_MS = 30_000;

const pendingInits = new WeakMap<object, Promise<boolean>>();

/**
 * Keycloak may only be initialised once per instance, so effect re-runs (StrictMode) share one init.
 * `keycloak.authenticated` defaults to false, so it cannot tell whether init already ran.
 */
function initKeycloakOnce(keycloak: ReturnType<typeof getKeycloak>) {
  let init = pendingInits.get(keycloak);
  if (!init) {
    init = keycloak.init({ onLoad: 'login-required', pkceMethod: 'S256', checkLoginIframe: false });
    pendingInits.set(keycloak, init);
  }
  return init;
}

/** Signs in through Keycloak, keeps the API token fresh and exposes the signed-in user. */
export function useAuthSession() {
  const [authenticated, setAuthenticated] = useState(false);
  const [user, setUser] = useState<User | null>(null);

  useEffect(() => {
    const keycloak = getKeycloak();
    let refreshTimer: ReturnType<typeof setInterval> | undefined;
    let cancelled = false;
    initKeycloakOnce(keycloak)
      .then((ok) => {
        if (cancelled) return;
        if (!ok || !keycloak.token) {
          void keycloak.login();
          return;
        }
        setAuthToken(keycloak.token);
        setSessionRefresher(async () => {
          try {
            // -1 forces a refresh: the server already rejected the current token.
            await keycloak.updateToken(-1);
            if (!keycloak.token) throw new Error('No token after refresh');
            return keycloak.token;
          } catch (error) {
            void keycloak.login();
            throw error;
          }
        });
        setUser(getUserFromToken(keycloak.tokenParsed));
        setAuthenticated(true);
        refreshTimer = window.setInterval(async () => {
          try {
            const refreshed = await keycloak.updateToken(60);
            if (refreshed && keycloak.token) setAuthToken(keycloak.token);
          } catch {
            void keycloak.login();
          }
        }, TOKEN_REFRESH_INTERVAL_MS);
      })
      .catch((error: unknown) => {
        // A failed init leaves keycloak-js without an adapter, so login() would throw and mask the real error.
        console.error('Keycloak initialisation failed', error);
      });
    return () => {
      cancelled = true;
      setSessionRefresher(null);
      if (refreshTimer) window.clearInterval(refreshTimer);
    };
  }, []);

  return { authenticated, user };
}
