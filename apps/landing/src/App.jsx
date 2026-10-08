import { useEffect, useState } from 'react';
import { getKeycloak, getUserFromToken } from './auth/keycloak.js';
import AuthStatusScreen from './screens/AuthStatusScreen/AuthStatusScreen.jsx';
import LandingPage from './screens/LandingPage/LandingPage.jsx';

export default function App() {
  const [authState, setAuthState] = useState({ status: 'initializing' });

  useEffect(() => {
    let mounted = true;
    let refreshTimer;
    const keycloak = getKeycloak();

    keycloak
      .init({
        onLoad: 'login-required',
        pkceMethod: 'S256',
        checkLoginIframe: false
      })
      .then((authenticated) => {
        if (!mounted) return;

        if (!authenticated || !keycloak.token) {
          keycloak.login();
          return;
        }

        setAuthState({
          status: 'authenticated',
          keycloak,
          user: getUserFromToken(keycloak.tokenParsed)
        });

        refreshTimer = window.setInterval(async () => {
          try {
            await keycloak.updateToken(60);
          } catch (error) {
            console.error('Token refresh failed', error);
            keycloak.login();
          }
        }, 30000);
      })
      .catch((error) => {
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

  return <AuthStatusScreen status={authState.status} error={authState.error} />;
}
