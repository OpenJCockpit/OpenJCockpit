import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { createBrowserRouter, RouterProvider } from 'react-router';
import { pulseApp } from '@openjcockpit/pulse-hub';
import { FakeHubSession, FakeHubSessionProvider } from '@openjcockpit/hub-contract/testing';
// Keeps the app entry (and so the contract import) reachable for the shared-dependency crawl.
import './app-entry';

// Standalone dev harness: runs the app without the host, behind a fake session.
const routes = await pulseApp.loadRoutes();
const router = createBrowserRouter([{ path: pulseApp.route, children: routes }]);

createRoot(document.getElementById('root') as HTMLElement).render(
  <StrictMode>
    <FakeHubSessionProvider session={new FakeHubSession()}>
      <RouterProvider router={router} />
    </FakeHubSessionProvider>
  </StrictMode>,
);
