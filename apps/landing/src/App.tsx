import { useEffect, useState } from 'react';
import type Keycloak from 'keycloak-js';
import { getKeycloak, getUserFromToken } from './auth/keycloak';
import type { User } from './auth/keycloak';
import AuthStatusScreen from './screens/AuthStatusScreen/AuthStatusScreen';
import LandingPage from './screens/LandingPage/LandingPage';

type AuthState =
  | { status: 'initializing' }
  | { status: 'authenticated'; keycloak: Keycloak; user: User }
  | { status: 'error'; error: unknown };

export default function App() {
  const [authState, setAuthState] = useState<AuthState>({ status: 'initializing' });

  useEffect(() => {
    let mounted = true;
    let refreshTimer: number | undefined;
    const keycloak = getKeycloak();

    keycloak
      .init({
        onLoad: 'login-required',
        pkceMethod: 'S256',
        checkLoginIframe: false,
      })
      .then((authenticated) => {
        if (!mounted) return;

        if (!authenticated || !keycloak.token) {
          void keycloak.login();
          return;
        }

        setAuthState({
          status: 'authenticated',
          keycloak,
          user: getUserFromToken(keycloak.tokenParsed),
        });

        refreshTimer = window.setInterval(async () => {
          try {
            await keycloak.updateToken(60);
          } catch (error) {
            console.error('Token refresh failed', error);
            void keycloak.login();
          }
        }, 30000);
      })
      .catch((error: unknown) => {
        console.error('Keycloak init failed', error);
        if (mounted) {
          setAuthState({ status: 'error', error });
        }
      });

    return () => {
      mounted = false;
      if (refreshTimer) {
        window.clearInterval(refreshTimer);
      }
    };
  }, []);

  if (authState.status === 'authenticated') {
    return <LandingPage user={authState.user} keycloak={authState.keycloak} />;
  }

  return (
    <AuthStatusScreen
      status={authState.status}
      error={authState.status === 'error' ? authState.error : undefined}
    />
  );
}
