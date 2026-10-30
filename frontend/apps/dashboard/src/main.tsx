import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router';
import { QueryClientProvider } from '@tanstack/react-query';
import '@openjcockpit/ui/styles.scss';
import { ErrorBoundary } from '@openjcockpit/ui';
import { errorCodeTranslator, setErrorTranslator } from '@openjcockpit/api';
import { loadKeycloakConfig } from '@openjcockpit/auth';
import {
  createI18n,
  detectLocale,
  I18nProvider,
  loadLocale,
  type Locale,
} from '@openjcockpit/i18n';
import App from './App';
import { appResources, loadAppLocale } from './i18nResources';
import { createQueryClient } from './queryClient';
import { RemoteAppsProvider } from './federation/RemoteApps';
import { fetchAllowedOrigins, fetchManifest, registerManifestRemotes } from './federation/manifest';
import { loadRemoteApps, type RemoteApps } from './federation/loadRemoteApps';

const queryClient = createQueryClient();
const i18n = createI18n({ locale: detectLocale(), resources: appResources });

// Keycloak settings come from the container at runtime; they must be loaded before the first login.
// The saved or detected language must be loaded before the first paint, or the UI would flash English.
// Server error codes are translated at the api boundary, so every caller shows the active language.
setErrorTranslator(
  errorCodeTranslator(
    (key) => i18n.getFixedT(null, 'api')(key as never),
    (key) => i18n.exists(key, { ns: 'api' }),
  ),
);

const localeReady = loadLocale(i18n, i18n.language as Locale, loadAppLocale).catch(() =>
  // A failed chunk download leaves the UI in English rather than blocking startup.
  i18n.changeLanguage('en'),
);

// A missing or broken manifest must not stop the host: it starts with no remote apps.
async function loadRemotes(): Promise<RemoteApps> {
  try {
    const [manifest, allowedOrigins] = await Promise.all([fetchManifest(), fetchAllowedOrigins()]);
    const { names, rejected } = registerManifestRemotes(manifest, allowedOrigins);
    const loaded = await loadRemoteApps(names);
    return { apps: loaded.apps, failed: [...rejected, ...loaded.failed] };
  } catch (error) {
    console.error('Federation manifest could not be loaded', error);
    return { apps: [], failed: [] };
  }
}

void Promise.all([loadKeycloakConfig(), localeReady, loadRemotes()]).then(([, , remotes]) => {
  createRoot(document.getElementById('root') as HTMLElement).render(
    <StrictMode>
      {/* The boundary sits outside the provider and keeps a literal English message, so a broken i18n setup still shows an error. */}
      <ErrorBoundary message="The application hit an unexpected error.">
        <I18nProvider i18n={i18n} loader={loadAppLocale}>
          <QueryClientProvider client={queryClient}>
            <BrowserRouter>
              <RemoteAppsProvider remotes={remotes}>
                <App />
              </RemoteAppsProvider>
            </BrowserRouter>
          </QueryClientProvider>
        </I18nProvider>
      </ErrorBoundary>
    </StrictMode>,
  );
});
