import { useEffect, useState } from 'react';
import { setAuthToken } from '@openjcockpit/api';
import { getKeycloak, getUserFromToken } from '@openjcockpit/auth';
import type { User } from '@openjcockpit/auth';

const TOKEN_REFRESH_INTERVAL_MS = 30_000;

/** Signs in through Keycloak, keeps the API token fresh and exposes the signed-in user. */
export function useAuthSession() {
  const [authenticated, setAuthenticated] = useState(false);
  const [user, setUser] = useState<User | null>(null);

  useEffect(() => {
    const keycloak = getKeycloak();
    let refreshTimer: ReturnType<typeof setInterval> | undefined;
    let cancelled = false;
    keycloak
      .init({ onLoad: 'login-required', pkceMethod: 'S256', checkLoginIframe: false })
      .then((ok) => {
        if (cancelled) return;
        if (!ok || !keycloak.token) {
          void keycloak.login();
          return;
        }
        setAuthToken(keycloak.token);
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
      .catch(() => {
        if (!cancelled) void keycloak.login();
      });
    return () => {
      cancelled = true;
      if (refreshTimer) window.clearInterval(refreshTimer);
    };
  }, []);

  return { authenticated, user };
}
