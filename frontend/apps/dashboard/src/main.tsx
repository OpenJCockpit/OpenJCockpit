import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router';
import { QueryClientProvider } from '@tanstack/react-query';
import '@openjcockpit/ui/styles.scss';
import { ErrorBoundary } from '@openjcockpit/ui';
import { loadKeycloakConfig } from '@openjcockpit/auth';
import { createI18n, detectLocale, I18nProvider } from '@openjcockpit/i18n';
import App from './App';
import { createQueryClient } from './queryClient';

const queryClient = createQueryClient();
const i18n = createI18n({ locale: detectLocale() });

// Keycloak settings come from the container at runtime; they must be loaded before the first login.
void loadKeycloakConfig().then(() => {
  createRoot(document.getElementById('root') as HTMLElement).render(
    <StrictMode>
      {/* The boundary sits outside the provider and keeps a literal English message, so a broken i18n setup still shows an error. */}
      <ErrorBoundary message="The application hit an unexpected error.">
        <I18nProvider i18n={i18n}>
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
