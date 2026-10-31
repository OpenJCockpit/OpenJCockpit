import { act, render as rtl, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useHubSession, type AppDefinition } from '@openjcockpit/hub-contract';
import { render } from '../testUtils/render';
import { Forbidden } from './Forbidden';
import { HubSessionProvider } from './HubSessionProvider';
import { RemoteAppsProvider, RemoteFailuresNotice, useRemoteApps } from './RemoteApps';
import { RemoteRoutes } from './RemoteRoutes';

const keycloak = vi.hoisted(() => ({
  token: undefined as string | undefined,
  tokenParsed: undefined as { realm_access: { roles: string[] } } | undefined,
  logout: vi.fn(() => Promise.resolve()),
}));
vi.mock('@openjcockpit/auth', () => ({ getKeycloak: () => keycloak }));

const noop = { apps: [], failed: [] };

describe('Forbidden', () => {
  it('shows the translated message', () => {
    render(
      <MemoryRouter>
        <Forbidden />
      </MemoryRouter>,
    );
    expect(screen.getByRole('alert')).toHaveTextContent('You do not have access to this page.');
    expect(screen.getByRole('button', { name: 'Back to dashboard' })).toBeInTheDocument();
  });
});

describe('RemoteFailuresNotice', () => {
  it('lists failed remotes', () => {
    render(
      <RemoteAppsProvider remotes={{ apps: [], failed: ['a', 'b'] }}>
        <RemoteFailuresNotice />
      </RemoteAppsProvider>,
    );
    expect(screen.getByRole('status')).toHaveTextContent('Some apps are unavailable: a, b');
  });

  it('renders nothing when every remote is up', () => {
    render(<RemoteFailuresNotice />);
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });
});

describe('RemoteAppsProvider', () => {
  it('exposes the loaded apps', () => {
    const app = { id: 'x' } as AppDefinition;
    function Probe() {
      return <p>{useRemoteApps().map((a) => a.id)}</p>;
    }
    rtl(
      <RemoteAppsProvider remotes={{ apps: [app], failed: [] }}>
        <Probe />
      </RemoteAppsProvider>,
    );
    expect(screen.getByText('x')).toBeInTheDocument();
    expect(noop.apps).toEqual([]);
  });
});

describe('HubSessionProvider', () => {
  beforeEach(() => {
    keycloak.token = 'secret-token';
    keycloak.tokenParsed = { realm_access: { roles: ['r1'] } };
    keycloak.logout.mockClear();
  });

  function Probe() {
    const session = useHubSession();
    return (
      <button type="button" onClick={() => void session.signOut()}>
        {session.user?.username ?? 'anon'}:{String(session.has('r1'))}:{String(session.has('r2'))}:
        {session.sessionId.length > 0 ? 'id' : ''}
      </button>
    );
  }

  it('adapts the Keycloak session', async () => {
    const user = { id: '1', username: 'tony', firstName: 'Tony', email: undefined };
    let token: string | undefined;
    function Token() {
      const session = useHubSession();
      void session.getToken().then((t) => (token = t));
      return null;
    }
    await act(async () => {
      rtl(
        <HubSessionProvider user={user}>
          <Probe />
          <Token />
        </HubSessionProvider>,
      );
    });
    expect(token).toBe('secret-token');
    const button = screen.getByRole('button');
    expect(button).toHaveTextContent('tony:true:false:id');
    button.click();
    expect(keycloak.logout).toHaveBeenCalled();
  });

  it('has no permissions without realm roles and no user while signed out', () => {
    keycloak.tokenParsed = undefined;
    rtl(
      <HubSessionProvider user={null}>
        <Probe />
      </HubSessionProvider>,
    );
    expect(screen.getByRole('button')).toHaveTextContent('anon:false:false:id');
  });
});

describe('RemoteRoutes', () => {
  function appWith(loadRoutes: AppDefinition['loadRoutes']): AppDefinition {
    return {
      id: 'x',
      labelKey: 'x',
      icon: 'x',
      route: '/x',
      order: 1,
      requiredPermissions: [],
      loadRoutes,
    };
  }

  async function mount(app: AppDefinition, entry = '/x') {
    let view!: ReturnType<typeof render>;
    await act(async () => {
      view = render(
        <MemoryRouter initialEntries={[entry]}>
          <Routes>
            <Route path="/x/*" element={<RemoteRoutes app={app} />} />
          </Routes>
        </MemoryRouter>,
      );
    });
    return view;
  }

  it('renders the remote routes relative to its path and loads them once', async () => {
    const loadRoutes = vi.fn(() =>
      Promise.resolve([
        { index: true, element: <p>home</p> },
        { path: 'sub', element: <p>sub page</p> },
      ]),
    );
    const app = appWith(loadRoutes);
    const first = await mount(app);
    expect(await screen.findByText('home')).toBeInTheDocument();
    first.unmount();
    await mount(app, '/x/sub');
    expect(await screen.findByText('sub page')).toBeInTheDocument();
    expect(loadRoutes).toHaveBeenCalledTimes(1);
  });

  it('shows a loading message while the routes load', async () => {
    await mount(appWith(() => new Promise(() => undefined)));
    expect(screen.getByText('Loading...')).toBeInTheDocument();
  });

  it('contains a failing remote in its error boundary', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    await mount(appWith(() => Promise.reject(new Error('boom'))));
    expect(await screen.findByTestId('error-boundary')).toBeInTheDocument();
  });
});
