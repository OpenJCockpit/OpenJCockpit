import { screen, waitFor } from '@testing-library/react';
import { chooseOption } from '@openjcockpit/ui/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { renderRoutedApp } from './testUtils/renderRoutedApp';
import { clickAsync } from './testUtils/dom';
import { mockDashboardApis, PROJECT, WORKFLOW } from './testUtils/dashboardFixtures';
import * as api from '@openjcockpit/api';

vi.mock(
  '@openjcockpit/landing-hub',
  async () => (await import('./testUtils/landingHubStub')).landingHubStub,
);

const createKeycloak = vi.hoisted(() => () => ({
  init: vi.fn(),
  token: 'fake-token',
  tokenParsed: { preferred_username: 'tstark', given_name: 'Tony' },
  updateToken: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
}));
let keycloak = createKeycloak();

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
  checkWorkflowPreflight: vi.fn(),
  listWorkflows: vi.fn(),
  listWorkflowGroups: vi.fn(),
}));

// Each lazy view is replaced by a stub exposing the callbacks App wires up, so these tests
// cover App's routing glue without rendering the feature libraries.
vi.mock('./lazyViews', async () => {
  const stub =
    (name: string, actions: Record<string, string>) => (props: Record<string, unknown>) => (
      <section data-testid={`view-${name}`}>
        {props.header as never}
        {props.initialExecution ? (
          <p data-testid="initial-execution">{JSON.stringify(props.initialExecution)}</p>
        ) : null}
        {Object.entries(actions).map(([label, prop]) => (
          <button key={label} type="button" onClick={() => (props[prop] as () => void)()}>
            {label}
          </button>
        ))}
      </section>
    );
  return {
    ProjectSelection: (props: {
      projects: { id: string; name: string }[];
      onSelect: (p: { id: string; name: string }) => void;
      onSettings: () => void;
    }) => (
      <section data-testid="view-projects">
        {props.projects.map((p) => (
          <button key={p.id} type="button" onClick={() => props.onSelect(p)}>
            Select {p.name}
          </button>
        ))}
        <button type="button" onClick={props.onSettings}>
          Open settings
        </button>
      </section>
    ),
    ProjectSettings: stub('settings', { 'Settings back': 'onBack' }),
    SkillsHubDashboard: stub('skills-hub', {
      'Skills back': 'onBack',
      'Skills settings': 'onSettings',
    }),
    SkillsHubSettings: stub('skills-hub-settings', { 'Skills settings back': 'onBack' }),
    SpecFilesDashboard: (props: {
      header: React.ReactNode;
      onBack: () => void;
      renderAddToQueue: (args: {
        project: unknown;
        fixedSpecFile: string;
        onAdded: () => void;
        onCancel: () => void;
      }) => React.ReactNode;
    }) => (
      <section data-testid="view-spec-files">
        {props.header}
        <button type="button" onClick={props.onBack}>
          Spec files back
        </button>
        {props.renderAddToQueue({
          project: { id: 'project-1' },
          fixedSpecFile: 'a.md',
          onAdded: () => {},
          onCancel: () => {},
        })}
      </section>
    ),
    SpecQueueDashboard: (props: {
      header: React.ReactNode;
      onBack: () => void;
      onOpenRun: (workflowId: string, runId: string) => void;
    }) => (
      <section data-testid="view-spec-queue">
        {props.header}
        <button type="button" onClick={props.onBack}>
          Queue back
        </button>
        <button type="button" onClick={() => props.onOpenRun('wf 1', 'run/2')}>
          Open run
        </button>
      </section>
    ),
    AddToQueueDialog: ({ fixedSpecFile }: { fixedSpecFile: string }) => (
      <p data-testid="add-to-queue">{fixedSpecFile}</p>
    ),
    WorkflowDashboard: stub('workflow', { 'Workflow back': 'onBack' }),
    WorkflowPromptDialog: () => null,
  };
});

describe('App routing', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
    keycloak = createKeycloak();
    keycloak.init.mockResolvedValue(true);
    keycloak.updateToken.mockResolvedValue(false);
    mockDashboardApis();
    vi.mocked(api.getSpecInitStatus).mockResolvedValue({ pending: false });
  });

  it('redirects unknown paths to the landing hub', async () => {
    renderRoutedApp('/nope');
    expect(await screen.findByTestId('landing-hero')).toBeInTheDocument();
  });

  it('opens workflow runs from the deep link with the run identifiers', async () => {
    renderRoutedApp('/workflow/wf-1/runs/run-9');
    expect(await screen.findByTestId('initial-execution')).toHaveTextContent(
      '{"workflowId":"wf-1","runId":"run-9"}',
    );
  });

  it('goes back from workflow to the dashboard when a project is selected', async () => {
    renderRoutedApp('/workflow');
    await clickAsync(await screen.findByRole('button', { name: 'Workflow back' }));
    expect(await screen.findByText('▶ Start')).toBeInTheDocument();
  });

  it('goes back from spec files and renders the add-to-queue dialog', async () => {
    renderRoutedApp('/spec-files');
    expect(await screen.findByTestId('add-to-queue')).toHaveTextContent('a.md');
    await clickAsync(screen.getByRole('button', { name: 'Spec files back' }));
    expect(await screen.findByText('▶ Start')).toBeInTheDocument();
  });

  it('opens a run from the spec queue with an encoded workflow path', async () => {
    renderRoutedApp('/spec-queue');
    await clickAsync(await screen.findByRole('button', { name: 'Open run' }));
    expect(await screen.findByTestId('view-workflow')).toBeInTheDocument();
  });

  it('goes back from the spec queue to the dashboard', async () => {
    renderRoutedApp('/spec-queue');
    await clickAsync(await screen.findByRole('button', { name: 'Queue back' }));
    expect(await screen.findByText('▶ Start')).toBeInTheDocument();
  });

  it('navigates between the skills hub and its settings', async () => {
    renderRoutedApp('/skills-hub');
    await clickAsync(await screen.findByRole('button', { name: 'Skills settings' }));
    await clickAsync(await screen.findByRole('button', { name: 'Skills settings back' }));
    expect(await screen.findByTestId('view-skills-hub')).toBeInTheDocument();
    await clickAsync(screen.getByRole('button', { name: 'Skills back' }));
    expect(await screen.findByText('▶ Start')).toBeInTheDocument();
  });

  it('sends the skills hub back to project selection when no project is chosen', async () => {
    localStorage.clear();
    renderRoutedApp('/skills-hub');
    await clickAsync(await screen.findByRole('button', { name: 'Skills back' }));
    expect(await screen.findByTestId('view-projects')).toBeInTheDocument();
  });

  it('returns from project settings to project selection without a project', async () => {
    localStorage.clear();
    renderRoutedApp('/settings');
    await clickAsync(await screen.findByRole('button', { name: 'Settings back' }));
    expect(await screen.findByTestId('view-projects')).toBeInTheDocument();
  });

  it('returns to the originally requested view after choosing a project', async () => {
    localStorage.clear();
    renderRoutedApp('/spec-queue');
    await clickAsync(await screen.findByRole('button', { name: 'Select Test Project' }));
    expect(await screen.findByTestId('view-spec-queue')).toBeInTheDocument();
    await waitFor(() => expect(localStorage.getItem('openjcockpit_project_id')).toBe('project-1'));
  });

  it('opens project settings from project selection', async () => {
    localStorage.clear();
    renderRoutedApp('/projects');
    await clickAsync(await screen.findByRole('button', { name: 'Open settings' }));
    expect(await screen.findByTestId('view-settings')).toBeInTheDocument();
  });

  it('forgets a stored project that no longer exists', async () => {
    localStorage.setItem('openjcockpit_project_id', 'gone');
    vi.mocked(api.loadProjects).mockResolvedValue([]);
    renderRoutedApp('/');
    await screen.findByTestId('landing-hero');
    await waitFor(() => expect(localStorage.getItem('openjcockpit_project_id')).toBeNull());
  });

  it('logs out through keycloak from the hub', async () => {
    renderRoutedApp('/');
    await clickAsync(await screen.findByRole('button', { name: 'Logout' }));
    expect(keycloak.logout).toHaveBeenCalledWith({ redirectUri: window.location.origin });
  });

  it('switches the active project from the header dropdown', async () => {
    const other = { ...PROJECT, id: 'project-2', name: 'Other Project' };
    vi.mocked(api.loadProjects).mockResolvedValue([PROJECT, other]);
    renderRoutedApp('/spec-queue');
    const select = await screen.findByRole('combobox', { name: 'Active project' });
    await clickAsync(select);
    await clickAsync(screen.getByRole('option', { name: 'Other Project' }));
    await waitFor(() => expect(localStorage.getItem('openjcockpit_project_id')).toBe('project-2'));
    expect(select).toHaveTextContent('Other Project');
  });

  it('opens project settings from the pre-flight alert on a Git problem', async () => {
    vi.mocked(api.checkWorkflowPreflight).mockResolvedValue({
      passed: false,
      errors: [{ code: 'GIT_AUTH_FAILED', message: 'denied' }],
    });
    renderRoutedApp('/dashboard');
    await clickAsync(await screen.findByText('▶ Start'));
    await clickAsync(await screen.findByRole('button', { name: 'Check Git settings' }));
    expect(await screen.findByTestId('view-settings')).toBeInTheDocument();
  });

  it('selects a project from project selection and lands on the dashboard', async () => {
    localStorage.clear();
    renderRoutedApp('/projects');
    await clickAsync(await screen.findByRole('button', { name: 'Select Test Project' }));
    expect(await screen.findByText('▶ Start')).toBeInTheDocument();
  });

  it('keeps the workflow the user picked in the launcher', async () => {
    const second = { ...WORKFLOW, id: 'wf-2', name: 'Second workflow' };
    vi.mocked(api.listWorkflows).mockResolvedValue([WORKFLOW, second]);
    renderRoutedApp('/dashboard');
    const picker = await screen.findByLabelText('Workflow');
    await waitFor(() => expect(picker).toHaveTextContent('Spec to pull request'));
    chooseOption(picker, 'Second workflow');
    expect(screen.getByLabelText('Workflow')).toHaveTextContent('Second workflow');
  });

  it('shows an error instead of the hero when the workspace fails to load', async () => {
    vi.mocked(api.loadWorkspace).mockRejectedValue(new Error('boom'));
    renderRoutedApp('/dashboard');
    expect(await screen.findByText('Failed to load the workspace.')).toBeInTheDocument();
  });
});

describe('App remote apps', () => {
  const remoteApp = (requiredPermissions: string[]) => ({
    id: 'hello',
    labelKey: 'x',
    icon: 'x',
    route: '/hello' as const,
    order: 1,
    requiredPermissions,
    loadRoutes: () => Promise.resolve([{ index: true, element: <p>hello remote</p> }]),
  });

  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
    keycloak = createKeycloak();
    keycloak.init.mockResolvedValue(true);
    keycloak.updateToken.mockResolvedValue(false);
    mockDashboardApis();
  });

  it('mounts a loaded remote app at its route', async () => {
    renderRoutedApp('/hello', { apps: [remoteApp([])], failed: [] });
    expect(await screen.findByText('hello remote')).toBeInTheDocument();
  });

  it('sends a user without the permission to the 403 page', async () => {
    renderRoutedApp('/hello', { apps: [remoteApp(['hello.read'])], failed: [] });
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'You do not have access to this page.',
    );
  });

  it('lists remotes that failed to load', async () => {
    renderRoutedApp('/', { apps: [], failed: ['broken'] });
    expect(await screen.findByRole('status')).toHaveTextContent('broken');
  });
});
