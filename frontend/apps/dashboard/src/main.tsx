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

void Promise.all([loadKeycloakConfig(), localeReady]).then(() => {
  createRoot(document.getElementById('root') as HTMLElement).render(
    <StrictMode>
      {/* The boundary sits outside the provider and keeps a literal English message, so a broken i18n setup still shows an error. */}
      <ErrorBoundary message="The application hit an unexpected error.">
        <I18nProvider i18n={i18n} loader={loadAppLocale}>
          <QueryClientProvider client={queryClient}>
            <BrowserRouter>
              <App />
            </BrowserRouter>
          </QueryClientProvider>
        </I18nProvider>
      </ErrorBoundary>
    </StrictMode>,
  );
});
