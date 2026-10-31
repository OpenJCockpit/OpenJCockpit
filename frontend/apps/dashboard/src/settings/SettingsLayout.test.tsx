import { act, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import {
  AppRegistryProvider,
  FORBIDDEN_PATH,
  type AppDefinition,
  type HubProject,
  type SettingsSectionDefinition,
} from '@openjcockpit/hub-contract';
import {
  FakeHubProjectProvider,
  FakeHubSession,
  FakeHubSessionProvider,
} from '@openjcockpit/hub-contract/testing';
import { render } from '../testUtils/render';
import { SettingsLayout } from './SettingsLayout';

const PROJECT = { id: 'p1', name: 'Project one' } as HubProject;

function section(
  id: string,
  over: Partial<SettingsSectionDefinition> = {},
  body: () => React.ReactNode = () => <p data-testid={`body-${id}`}>{id} body</p>,
): SettingsSectionDefinition {
  return {
    id,
    sourceAppId: 'x',
    titleKey: `settings.sections.${id}.title`,
    requiredPermissions: [],
    order: 10,
    groupKey: 'settings.groups.workspace',
    component: () => Promise.resolve({ default: body }),
    ...over,
  };
}

function app(settings: SettingsSectionDefinition[], over: Partial<AppDefinition> = {}) {
  return {
    id: 'x',
    labelKey: 'siteHeader.nav.platform',
    icon: 'x',
    route: '/x',
    order: 1,
    requiredPermissions: [],
    showInNav: false,
    loadRoutes: () => Promise.resolve([]),
    settings,
    ...over,
  } satisfies AppDefinition;
}

function Where() {
  return <p data-testid="where">{useLocation().pathname}</p>;
}

async function renderSettings(
  apps: AppDefinition[],
  at: string,
  { permissions = [] as string[], project = true } = {},
) {
  await act(async () => {
    render(
      <MemoryRouter initialEntries={[at]}>
        <FakeHubSessionProvider session={new FakeHubSession(permissions)}>
          <FakeHubProjectProvider selected={project ? PROJECT : null}>
            <AppRegistryProvider apps={apps}>
              <Routes>
                <Route path="/settings/*" element={<SettingsLayout screen="settings" />} />
                <Route path="/project-settings/*" element={<SettingsLayout screen="project" />} />
                <Route path={FORBIDDEN_PATH} element={<p data-testid="forbidden" />} />
              </Routes>
              <Where />
            </AppRegistryProvider>
          </FakeHubProjectProvider>
        </FakeHubSessionProvider>
      </MemoryRouter>,
    );
  });
}

describe('SettingsLayout', () => {
  it('redirects /settings to the first visible section', async () => {
    await renderSettings(
      [app([section('projects'), section('other', { order: 20 })])],
      '/settings',
    );
    expect(await screen.findByTestId('body-projects')).toBeInTheDocument();
    expect(screen.getByTestId('where')).toHaveTextContent('/settings/projects');
  });

  it('renders the requested section under a host heading and lists the others in the nav', async () => {
    await renderSettings(
      [app([section('projects'), section('skillsHub', { order: 20 })])],
      '/settings/skillsHub',
    );
    expect(await screen.findByTestId('body-skillsHub')).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 1, name: 'Marketplaces' })).toBeInTheDocument();
    const nav = screen.getByTestId('settings-nav');
    expect(nav).toHaveTextContent('Project management');
    expect(screen.getByTestId('settings-nav-skillsHub')).toHaveAttribute('aria-current', 'page');
  });

  it('keeps project sections off the Settings screen and on their own screen', async () => {
    const apps = [
      app([section('skills'), section('jira', { screen: 'project', requiresProject: true })]),
    ];
    await renderSettings(apps, '/settings');
    expect(await screen.findByTestId('body-skills')).toBeInTheDocument();
    expect(screen.queryByTestId('settings-nav-jira')).not.toBeInTheDocument();
  });

  it('redirects /project-settings to its first section and lists only project sections', async () => {
    const apps = [
      app([section('skills'), section('jira', { screen: 'project', requiresProject: true })]),
    ];
    await renderSettings(apps, '/project-settings');
    expect(await screen.findByTestId('body-jira')).toBeInTheDocument();
    expect(screen.getByTestId('where')).toHaveTextContent('/project-settings/jira');
    expect(screen.getByTestId('project-settings')).toBeInTheDocument();
    expect(screen.queryByTestId('settings-nav-skills')).not.toBeInTheDocument();
  });

  it('answers 404 for a section that belongs to the other screen', async () => {
    const apps = [app([section('skills'), section('jira', { screen: 'project' })])];
    await renderSettings(apps, '/settings/jira');
    expect(await screen.findByTestId('not-found')).toBeInTheDocument();
  });

  it('shows a card-sized loading line, not a whole page with its own shell, while a section loads', async () => {
    const pending = section('slow', { component: () => new Promise(() => undefined) });
    await renderSettings([app([pending])], '/settings/slow');
    const card = await screen.findByTestId('settings-card-slow');
    expect(card).toHaveTextContent('Loading…');
    expect(screen.queryByTestId('view-loading')).not.toBeInTheDocument();
    expect(document.querySelectorAll('main')).toHaveLength(1);
  });

  it('puts the section content on a card', async () => {
    await renderSettings([app([section('projects')])], '/settings/projects');
    expect(await screen.findByTestId('settings-card-projects')).toContainElement(
      await screen.findByTestId('body-projects'),
    );
  });

  it('sets the document title from the section', async () => {
    await renderSettings([app([section('projects')])], '/settings/projects');
    await screen.findByTestId('body-projects');
    expect(document.title).toBe('Settings · Project management · OpenJCockpit');
  });

  it('answers 404 for an unknown id', async () => {
    await renderSettings([app([section('projects')])], '/settings/nope');
    expect(await screen.findByTestId('not-found')).toBeInTheDocument();
  });

  it('answers 403 for a section the session may not open and hides it from the nav', async () => {
    const apps = [
      app([section('projects'), section('secret', { requiredPermissions: ['admin'] })]),
    ];
    await renderSettings(apps, '/settings/secret');
    expect(await screen.findByTestId('forbidden')).toBeInTheDocument();
  });

  it('keeps hidden sections out of the nav', async () => {
    const apps = [
      app([section('projects'), section('secret', { requiredPermissions: ['admin'] })]),
    ];
    await renderSettings(apps, '/settings/projects');
    await screen.findByTestId('body-projects');
    expect(screen.queryByTestId('settings-nav-secret')).not.toBeInTheDocument();
  });

  it('shows an empty state when no section is visible', async () => {
    await renderSettings([app([])], '/settings');
    expect(screen.getByTestId('settings-empty')).toBeInTheDocument();
  });

  it('shows the select-project message for a project section without a project', async () => {
    await renderSettings(
      [app([section('projects'), section('jira', { requiresProject: true, order: 20 })])],
      '/settings/jira',
      {
        project: false,
      },
    );
    expect(await screen.findByTestId('settings-select-project')).toBeInTheDocument();
    expect(screen.queryByTestId('body-jira')).not.toBeInTheDocument();
    expect(screen.getByTestId('settings-nav-jira')).toBeInTheDocument();
  });

  it('keeps the nav when a section throws', async () => {
    const error = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    const boom = section('boom', {}, () => {
      throw new Error('boom');
    });
    await renderSettings([app([section('projects'), boom])], '/settings/boom');
    await waitFor(() => expect(screen.getByRole('alert')).toBeInTheDocument());
    expect(screen.getByTestId('settings-nav-projects')).toBeInTheDocument();
    error.mockRestore();
  });
});
