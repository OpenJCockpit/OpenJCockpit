import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { createBrowserRouter, RouterProvider } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import '@openjcockpit/ui/styles.scss';
import type { Project } from '@openjcockpit/api';
import { createI18n, detectLocale, I18nProvider } from '@openjcockpit/i18n';
import { specsApps } from './specsApps';
import { FakeHubSession, FakeHubSessionProvider } from '@openjcockpit/hub-contract/testing';
import { FakeHubProjectProvider } from '@openjcockpit/hub-contract/testing';
// Keeps the app entry (and so the contract import) reachable for the shared-dependency crawl.
import './app-entry';

// Standalone dev harness: runs the app without the host, behind a fake session and no host header.
const router = createBrowserRouter(
  await Promise.all(
    specsApps.map(async (app) => ({ path: `${app.route}/*`, children: await app.loadRoutes() })),
  ),
);
const i18n = createI18n({ locale: detectLocale(), resources: {} });
const queryClient = new QueryClient();
// The harness has no host to pick a project, so it works in a fixed one.
const DEMO_PROJECT = { id: 'demo', name: 'Demo project' } as Project;

createRoot(document.getElementById('root') as HTMLElement).render(
  <StrictMode>
    <I18nProvider i18n={i18n}>
      <QueryClientProvider client={queryClient}>
        <FakeHubSessionProvider session={new FakeHubSession()}>
          <FakeHubProjectProvider selected={DEMO_PROJECT}>
            <RouterProvider router={router} />
          </FakeHubProjectProvider>
        </FakeHubSessionProvider>
      </QueryClientProvider>
    </I18nProvider>
  </StrictMode>,
);
