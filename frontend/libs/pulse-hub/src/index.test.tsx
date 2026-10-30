import { render, screen } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { describe, expect, it } from 'vitest';
import { isAppDefinition } from '@openjcockpit/hub-contract';
import { FakeHubSession, FakeHubSessionProvider } from '@openjcockpit/hub-contract/testing';
import { pulseApp } from './index';
import { PULSE_BADGE_COUNT, usePulseBadge } from './usePulseBadge';
import { PulseTopbar } from './PulseTopbar';
import { PulseWidget } from './PulseWidget';

describe('pulseApp', () => {
  it('is a valid app definition', () => {
    expect(isAppDefinition(pulseApp)).toBe(true);
    expect(pulseApp).toMatchObject({ id: 'pulse', route: '/pulse', requiredPermissions: [] });
  });

  it('mounts its real routes and shows the session id but never the token', async () => {
    const routes = await pulseApp.loadRoutes();
    const router = createMemoryRouter(routes, { initialEntries: ['/'] });
    const { container } = render(
      <FakeHubSessionProvider session={new FakeHubSession()}>
        <RouterProvider router={router} />
      </FakeHubSessionProvider>,
    );
    expect(await screen.findByTestId('session-id')).toHaveTextContent('fake-session');
    expect(screen.getByTestId('username')).toHaveTextContent('fake');
    expect(container.textContent).not.toContain('fake-token');
  });

  it('shows a placeholder without a user', async () => {
    const session = new FakeHubSession();
    session.user = undefined;
    const routes = await pulseApp.loadRoutes();
    render(
      <FakeHubSessionProvider session={session}>
        <RouterProvider router={createMemoryRouter(routes)} />
      </FakeHubSessionProvider>,
    );
    expect(await screen.findByTestId('username')).toHaveTextContent('-');
  });

  it('registers a badge, a widget, a quick action and a top bar item', async () => {
    expect(usePulseBadge()).toBe(PULSE_BADGE_COUNT);
    expect(pulseApp.useBadge).toBe(usePulseBadge);
    expect(pulseApp.quickActions?.[0]).toMatchObject({ link: '/pulse' });
    const widget = await pulseApp.widgets?.[0]?.component();
    const topbar = await pulseApp.topbar?.[0]?.component();
    expect(widget?.default).toBe(PulseWidget);
    expect(topbar?.default).toBe(PulseTopbar);
  });

  it('renders the widget for the user and the top bar chip', () => {
    render(
      <FakeHubSessionProvider session={new FakeHubSession()}>
        <PulseWidget />
        <PulseTopbar />
      </FakeHubSessionProvider>,
    );
    expect(screen.getByTestId('pulse-widget')).toHaveTextContent('fake');
    expect(screen.getByTestId('pulse-topbar')).toBeInTheDocument();
  });

  it('greets an unknown user generically', () => {
    const session = new FakeHubSession();
    session.user = undefined;
    render(
      <FakeHubSessionProvider session={session}>
        <PulseWidget />
      </FakeHubSessionProvider>,
    );
    expect(screen.getByTestId('pulse-widget')).toHaveTextContent('for you');
  });
});
