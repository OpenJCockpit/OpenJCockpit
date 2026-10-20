import { render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import App from './App';
import * as api from '@openjcockpit/api';
import { clickAsync } from './testUtils/dom';
import { mockDashboardApis } from './testUtils/dashboardFixtures';

vi.mock(
  '@openjcockpit/landing-hub',
  async () => (await import('./testUtils/landingHubStub')).landingHubStub,
);

const keycloak = vi.hoisted(() => ({
  init: vi.fn(),
  token: 'fake-token',
  tokenParsed: { preferred_username: 'tstark', given_name: 'Tony' },
  updateToken: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
}));

vi.mock('@openjcockpit/auth', async () => ({
  ...(await vi.importActual<typeof import('@openjcockpit/auth')>('@openjcockpit/auth')),
  getKeycloak: () => keycloak,
}));

vi.mock('@openjcockpit/api', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@openjcockpit/api')>()),
  loadProjects: vi.fn(),
  loadWorkspace: vi.fn(),
  loadProjectSpecs: vi.fn(),
  getSpecInitStatus: vi.fn(),
  listWorkflows: vi.fn(),
  listWorkflowGroups: vi.fn(),
}));

describe('landing hub entry view', () => {
  beforeEach(() => {
    localStorage.clear();
    window.history.replaceState({}, '', '/');
    vi.clearAllMocks();
    keycloak.init.mockResolvedValue(true);
    keycloak.updateToken.mockResolvedValue(false);
    mockDashboardApis();
    vi.mocked(api.getSpecInitStatus).mockResolvedValue({ pending: false });
  });

  it('shows the hub with the user after login', async () => {
    render(<App />);

    expect(await screen.findByTestId('landing-hero')).toBeInTheDocument();
    expect(screen.getByText('Hub for Tony')).toBeInTheDocument();
  });

  it('opens the dashboard from the Implementation orb and returns via the header', async () => {
    render(<App />);
    await clickAsync(await screen.findByTestId('portal-orb-implementation'));
    expect(await screen.findByText('▶ Start')).toBeInTheDocument();

    await clickAsync(screen.getByTestId('nav-hub'));

    expect(await screen.findByTestId('landing-hero')).toBeInTheDocument();
  });

  it('opens spec files from the Business orb', async () => {
    render(<App />);
    await clickAsync(await screen.findByTestId('portal-orb-business'));

    expect(screen.queryByTestId('landing-hero')).not.toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: 'Specs' })).toBeInTheDocument();
  });

  it('goes to project selection when no project is stored', async () => {
    localStorage.clear();
    render(<App />);
    await clickAsync(await screen.findByTestId('portal-orb-implementation'));

    expect(screen.queryByTestId('landing-hero')).not.toBeInTheDocument();
  });

  it('bypasses the hub on the spec-files deep link', async () => {
    window.history.replaceState({}, '', '/?view=spec-files');
    render(<App />);

    await waitFor(() => expect(api.loadProjects).toHaveBeenCalled());
    await screen.findByRole('heading', { name: 'Specs' });
    expect(screen.queryByTestId('landing-hero')).not.toBeInTheDocument();
  });

  it('initialises Keycloak once, even after a portal consumes a deep link', async () => {
    render(<App />);
    await clickAsync(await screen.findByTestId('portal-orb-business'));

    expect(keycloak.init).toHaveBeenCalledTimes(1);
  });

  it('logs out from the hub and from the dashboard', async () => {
    render(<App />);
    await clickAsync(await screen.findByText('Logout'));
    expect(keycloak.logout).toHaveBeenCalledWith({ redirectUri: window.location.origin });

    keycloak.logout.mockClear();
    await clickAsync(screen.getByTestId('portal-orb-implementation'));
    await clickAsync(await screen.findByTestId('nav-logout'));
    expect(keycloak.logout).toHaveBeenCalledWith({ redirectUri: window.location.origin });
  });
});
