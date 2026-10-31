import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { createBrowserRouter, RouterProvider } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import '@openjcockpit/ui/styles.scss';
import { createI18n, detectLocale, I18nProvider } from '@openjcockpit/i18n';
import { skillsHubApp } from '@openjcockpit/skills-hub';
import { FakeHubSession, FakeHubSessionProvider } from '@openjcockpit/hub-contract/testing';
import { FakeHubProjectProvider } from '@openjcockpit/hub-contract/testing';
// Keeps the app entry (and so the contract import) reachable for the shared-dependency crawl.
import './app-entry';

// Standalone dev harness: runs the app without the host, behind a fake session and no host header.
const routes = await skillsHubApp.loadRoutes();
const router = createBrowserRouter([{ path: skillsHubApp.route, children: routes }]);
const i18n = createI18n({ locale: detectLocale(), resources: {} });
const queryClient = new QueryClient();

createRoot(document.getElementById('root') as HTMLElement).render(
  <StrictMode>
    <I18nProvider i18n={i18n}>
      <QueryClientProvider client={queryClient}>
        <FakeHubSessionProvider session={new FakeHubSession()}>
          <FakeHubProjectProvider>
            <RouterProvider router={router} />
          </FakeHubProjectProvider>
        </FakeHubSessionProvider>
      </QueryClientProvider>
    </I18nProvider>
  </StrictMode>,
);
