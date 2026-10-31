import { fireEvent, screen } from '@testing-library/react';
import { AppRegistryProvider, type AppDefinition } from '@openjcockpit/hub-contract';
import { FakeHubSession, FakeHubSessionProvider } from '@openjcockpit/hub-contract/testing';
import { render } from '../../testUtils/render';
import type { ReactNode } from 'react';
import { MemoryRouter, useLocation } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import type { Project } from '@openjcockpit/api';
import { ROUTES } from '../../routes';
import { FEATURE_FLAGS } from '../../featureFlags';
import { SiteHeader } from './SiteHeader';
import type { SiteSection } from './SiteHeader';

function project(id: string, name: string): Project {
  return { id, name, active: 1, gitStatus: 'ACCESSIBLE' } as Project;
}

function Registry({ apps = [], children }: { apps?: AppDefinition[]; children: ReactNode }) {
  return (
    <FakeHubSessionProvider session={new FakeHubSession()}>
      <AppRegistryProvider apps={apps}>{children}</AppRegistryProvider>
    </FakeHubSessionProvider>
  );
}

function remoteApp(over: Partial<AppDefinition> = {}): AppDefinition {
  return {
    id: 'pulse',
    labelKey: 'home.eyebrow',
    icon: 'pulse',
    route: '/pulse',
    order: 1,
    requiredPermissions: [],
    loadRoutes: () => Promise.resolve([]),
    ...over,
  };
}

function settingsApp(screen?: 'settings' | 'project'): AppDefinition {
  return remoteApp({
    id: 'prefs',
    showInNav: false,
    settings: [
      {
        id: 'general',
        sourceAppId: 'prefs',
        titleKey: 'home.eyebrow',
        requiredPermissions: [],
        order: 1,
        screen,
        component: () => Promise.resolve({ default: () => null }),
      },
    ],
  });
}

function Location() {
  return <p data-testid="location">{useLocation().pathname}</p>;
}

function setup(
  props: Partial<{
    active: SiteSection;
    projects: Project[];
    selectedProject: Project | null;
    apps: AppDefinition[];
    at: string;
    user: { firstName: string; username: string };
  }> = {},
) {
  const onProjectChange = vi.fn();
  const onLogout = vi.fn();
  render(
    <MemoryRouter initialEntries={[props.at ?? '/start']}>
      <Registry apps={props.apps}>
        <SiteHeader
          active={props.active ?? 'platform'}
          projects={props.projects ?? []}
          selectedProject={props.selectedProject ?? null}
          onProjectChange={onProjectChange}
          onLogout={onLogout}
          user={props.user}
        />
        <Location />
      </Registry>
    </MemoryRouter>,
  );
  return { onProjectChange, onLogout };
}

const path = () => screen.getByTestId('location').textContent;

describe('SiteHeader', () => {
  it.each([
    ['nav-platform', ROUTES.dashboard],
    ['nav-hub', ROUTES.home],
  ])('%s navigates to %s', (testId, route) => {
    localStorage.setItem(FEATURE_FLAGS.hub, '1');
    setup();
    fireEvent.click(screen.getByTestId(testId));
    expect(path()).toBe(route);
  });

  it('opens settings from the gear', () => {
    setup({ apps: [settingsApp()] });
    fireEvent.click(screen.getByTitle('Settings'));
    expect(path()).toBe(ROUTES.settings);
  });

  it('opens project settings from its own entry, shown only when a project section exists', () => {
    setup({ apps: [settingsApp('project')] });
    expect(screen.queryByTitle('Settings')).not.toBeInTheDocument();
    fireEvent.click(screen.getByTitle('Project settings'));
    expect(path()).toBe(ROUTES.projectSettings);
  });

  it('marks project settings as current on any project settings path', () => {
    setup({ apps: [settingsApp('project')], at: '/project-settings/general' });
    expect(screen.getByTitle('Project settings')).toHaveAttribute('aria-current', 'page');
  });

  it('hides the settings entry when no app contributes a section', () => {
    setup({ apps: [remoteApp()] });
    expect(screen.queryByTitle('Settings')).not.toBeInTheDocument();
  });

  it('marks settings as current on any settings path', () => {
    setup({ apps: [settingsApp()], at: '/settings/general' });
    expect(screen.getByTitle('Settings')).toHaveAttribute('aria-current', 'page');
  });

  it('returns to the hub from the logo', () => {
    setup();
    fireEvent.click(screen.getAllByTitle('Back to hub')[0]!);
    expect(path()).toBe(ROUTES.dashboard);
  });

  it('marks only the current page, whether host or remote', () => {
    setup({ active: 'platform', apps: [remoteApp()] });
    expect(screen.getByTestId('nav-platform')).toHaveAttribute('aria-current', 'page');
    expect(screen.getByTestId('nav-app-pulse')).not.toHaveAttribute('aria-current');
  });

  it('collapses to icons only and remembers the choice', () => {
    window.localStorage.clear();
    setup({ projects: [project('a', 'Alpha')] });
    expect(screen.getByRole('combobox', { name: 'Active project' })).toBeInTheDocument();
    fireEvent.click(screen.getByTestId('nav-collapse'));
    expect(screen.getByTestId('nav-collapse')).toHaveAttribute('aria-label', 'Expand');
    expect(screen.queryByRole('combobox', { name: 'Active project' })).not.toBeInTheDocument();
    expect(screen.getByTestId('nav-platform')).toHaveAttribute('title', 'Platform');
    expect(window.localStorage.getItem('openjcockpit.sidebarCollapsed')).toBe('1');
    fireEvent.click(screen.getByTestId('nav-collapse'));
    expect(screen.getByRole('combobox', { name: 'Active project' })).toBeInTheDocument();
  });

  it.each([
    [{ firstName: 'Tony', username: 'tstark' }, 'Tony', 'T'],
    [{ firstName: '', username: 'tstark' }, 'tstark', 'T'],
  ])('shows the signed-in user %j as %s', (user, name, initial) => {
    setup({ user });
    expect(screen.getAllByText(name).length).toBeGreaterThan(0);
    expect(screen.getAllByText('tstark')).not.toHaveLength(0);
    expect(document.querySelector('[aria-hidden="true"][title]')).toHaveTextContent(initial);
  });

  it('starts expanded when the stored preference cannot be read', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('denied');
    });
    setup();
    expect(screen.getByTestId('nav-collapse')).toHaveAttribute('aria-label', 'Collapse');
    vi.restoreAllMocks();
  });

  it('still collapses when the preference cannot be stored', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('quota');
    });
    setup();
    fireEvent.click(screen.getByTestId('nav-collapse'));
    expect(screen.getByTestId('nav-collapse')).toHaveAttribute('aria-label', 'Expand');
    vi.restoreAllMocks();
  });

  it('logs out', () => {
    const { onLogout } = setup();
    fireEvent.click(screen.getByTestId('nav-logout'));
    expect(onLogout).toHaveBeenCalledTimes(1);
  });

  it('hides the project picker when there are no projects', () => {
    setup();
    expect(screen.queryByRole('combobox', { name: 'Active project' })).not.toBeInTheDocument();
  });

  it('shows the selected project and reports changes', () => {
    const a = project('a', 'Alpha');
    const b = project('b', 'Beta');
    const { onProjectChange } = setup({ projects: [a, b], selectedProject: b });
    const select = screen.getByRole('combobox', { name: 'Active project' });
    expect(select).toHaveTextContent('Beta');
    fireEvent.click(select);
    fireEvent.click(screen.getByRole('option', { name: 'Alpha' }));
    expect(onProjectChange).toHaveBeenCalledWith('a');
  });

  it('leaves projects that cannot be selected out of the project list', () => {
    const a = project('a', 'Alpha');
    const off = { ...project('off', 'Offline'), active: 0 };
    setup({ projects: [a, off], selectedProject: a });
    fireEvent.click(screen.getByRole('combobox', { name: 'Active project' }));
    expect(screen.queryByRole('option', { name: 'Offline' })).not.toBeInTheDocument();
  });

  it('explains what a nav badge counts', () => {
    setup({ apps: [remoteApp({ useBadge: () => 2, badgeLabelKey: 'home.eyebrow' })] });
    expect(screen.getByText('2')).toHaveAttribute('title', 'Home');
  });

  it('lists remote apps in the nav with their badge, and opens them', () => {
    const app = remoteApp({ useBadge: () => 4 });
    setup({ apps: [app] });
    const item = screen.getByTestId('nav-app-pulse');
    expect(item).toHaveTextContent('Home');
    expect(item).toHaveTextContent('4');
    fireEvent.click(item);
    expect(path()).toBe('/pulse');
  });

  it('marks a remote app current on its own routes and shows no badge when it has none', () => {
    setup({ apps: [remoteApp()], at: '/pulse/details' });
    const item = screen.getByTestId('nav-app-pulse');
    expect(item).toHaveAttribute('aria-current', 'page');
    expect(item).not.toHaveTextContent(/\d/);
  });

  it('keeps hidden-from-nav apps out and falls back to a generic icon', () => {
    setup({
      apps: [remoteApp({ showInNav: false }), remoteApp({ id: 'other', icon: 'unknown-icon' })],
    });
    expect(screen.queryByTestId('nav-app-pulse')).not.toBeInTheDocument();
    expect(screen.getByTestId('nav-app-other')).toBeInTheDocument();
  });

  it('titles a remote nav item with its label so a truncated label stays readable', () => {
    setup({ apps: [remoteApp()] });
    expect(screen.getByTestId('nav-app-pulse')).toHaveAttribute('title', 'Home');
    fireEvent.click(screen.getByTestId('nav-collapse'));
    expect(screen.getByTestId('nav-app-pulse')).toHaveAttribute('title', 'Home');
  });
});
