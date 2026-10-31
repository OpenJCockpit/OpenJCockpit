import { render, screen } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { describe, expect, it } from 'vitest';
import { isAppDefinition } from '@openjcockpit/hub-contract';
import { FakeHubSession, FakeHubSessionProvider } from '@openjcockpit/hub-contract/testing';
import { helloApp } from './index';

describe('helloApp', () => {
  it('is a valid app definition', () => {
    expect(isAppDefinition(helloApp)).toBe(true);
    expect(helloApp).toMatchObject({ id: 'hello', route: '/hello', requiredPermissions: [] });
  });

  it('mounts its real routes and shows the session id but never the token', async () => {
    const routes = await helloApp.loadRoutes();
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
    const routes = await helloApp.loadRoutes();
    render(
      <FakeHubSessionProvider session={session}>
        <RouterProvider router={createMemoryRouter(routes)} />
      </FakeHubSessionProvider>,
    );
    expect(await screen.findByTestId('username')).toHaveTextContent('-');
  });
});

describe('helloApp contributions', () => {
  it('renders the widget with the signed-in user', async () => {
    const Widget = (await helloApp.widgets![0]!.component()).default;
    render(
      <FakeHubSessionProvider session={new FakeHubSession()}>
        <Widget />
      </FakeHubSessionProvider>,
    );
    expect(screen.getByTestId('hello-widget')).toHaveTextContent('Hello, fake');
  });

  it('renders the widget for a guest', async () => {
    const session = new FakeHubSession();
    session.user = undefined;
    const Widget = (await helloApp.widgets![0]!.component()).default;
    render(
      <FakeHubSessionProvider session={session}>
        <Widget />
      </FakeHubSessionProvider>,
    );
    expect(screen.getByTestId('hello-widget')).toHaveTextContent('Hello, guest');
  });
});
