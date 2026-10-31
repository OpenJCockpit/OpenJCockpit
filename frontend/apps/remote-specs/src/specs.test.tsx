import { act, render, screen } from '@testing-library/react';
import type { ReactNode } from 'react';
import { createMemoryRouter, RouterProvider, useLocation } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import type { Project } from '@openjcockpit/api';
import { DASHBOARD_PATH, HubHeaderContext, isAppDefinition } from '@openjcockpit/hub-contract';
import { FakeHubProjectProvider } from '@openjcockpit/hub-contract/testing';
import { specsApps } from './specsApps';

// The feature libs are stood in for by plain views, so these tests cover only what this remote
// composes: the guard, the back target, the header, the dialog and the run link.
vi.mock('@openjcockpit/spec-files', () => ({
  WithSpecFilesTranslations: ({ children }: { children: ReactNode }) => children,
  SpecFilesDashboard: (props: {
    project: Project;
    header: ReactNode;
    onBack: () => void;
    renderAddToQueue: (args: {
      project: Project;
      fixedSpecFile: string;
      onAdded: () => void;
      onCancel: () => void;
    }) => ReactNode;
  }) => (
    <section>
      <p>spec-files of {props.project.name}</p>
      {props.header}
      <button onClick={props.onBack}>files back</button>
      {props.renderAddToQueue({
        project: props.project,
        fixedSpecFile: 'a.md',
        onAdded: () => undefined,
        onCancel: () => undefined,
      })}
    </section>
  ),
}));
vi.mock('@openjcockpit/spec-queue', () => ({
  WithSpecQueueTranslations: ({ children }: { children: ReactNode }) => children,
  AddToQueueDialog: (props: { fixedSpecFile: string }) => <p>dialog for {props.fixedSpecFile}</p>,
  SpecQueueDashboard: (props: {
    project: Project;
    onBack: () => void;
    onOpenRun: (workflowId: string, runId: string) => void;
    onSettings: () => void;
  }) => (
    <section>
      <p>spec-queue of {props.project.name}</p>
      <button onClick={props.onSettings}>queue settings</button>
      <button onClick={props.onBack}>queue back</button>
      <button onClick={() => props.onOpenRun('wf 1', 'r/2')}>open run</button>
    </section>
  ),
}));
vi.mock('@openjcockpit/workflow', () => ({
  WithWorkflowTranslations: ({ children }: { children: ReactNode }) => children,
  WorkflowDashboard: (props: {
    initialExecution?: { workflowId: string; runId: string };
    onBack: () => void;
  }) => (
    <section>
      <p>
        workflow {props.initialExecution ? `${props.initialExecution.workflowId} run` : 'overview'}
      </p>
      <button onClick={props.onBack}>workflow back</button>
    </section>
  ),
}));

const PROJECT = { id: 'p', name: 'Proj' } as Project;

function Where() {
  return <p data-testid="where">{useLocation().pathname}</p>;
}

async function open(path: string, selected: Project | null = PROJECT) {
  const routes = await Promise.all(
    specsApps.map(async (app) => ({ path: `${app.route}/*`, children: await app.loadRoutes() })),
  );
  const router = createMemoryRouter([...routes, { path: '*', element: <Where /> }], {
    initialEntries: [path],
  });
  await act(async () => {
    render(
      <HubHeaderContext.Provider value={<p>host header</p>}>
        <FakeHubProjectProvider selected={selected}>
          <RouterProvider router={router} />
        </FakeHubProjectProvider>
      </HubHeaderContext.Provider>,
    );
  });
  return router;
}

describe('specsApps', () => {
  it('are valid app definitions in their old menu order', () => {
    expect(specsApps.every(isAppDefinition)).toBe(true);
    expect(specsApps.map((app) => [app.route, app.order])).toEqual([
      ['/workflow', 20],
      ['/spec-files', 30],
      ['/spec-queue', 40],
    ]);
  });

  it.each(['/workflow', '/spec-files', '/spec-queue'])(
    'sends %s to project selection, remembering the way back, without a project',
    async (path) => {
      const router = await open(path, null);
      expect(router.state.location.pathname).toBe('/projects');
      expect(router.state.location.state).toEqual({ from: path });
    },
  );
});

describe('spec files', () => {
  it('shows the project, the host header and the add-to-queue dialog, and goes back', async () => {
    const router = await open('/spec-files');
    expect(screen.getByText('spec-files of Proj')).toBeInTheDocument();
    expect(screen.getByText('host header')).toBeInTheDocument();
    expect(screen.getByText('dialog for a.md')).toBeInTheDocument();
    await act(async () => screen.getByText('files back').click());
    expect(router.state.location.pathname).toBe(DASHBOARD_PATH);
  });
});

describe('spec queue', () => {
  it('goes back, and opens a run with its ids encoded', async () => {
    const router = await open('/spec-queue');
    await act(async () => screen.getByText('open run').click());
    expect(router.state.location.pathname).toBe('/workflow/wf%201/runs/r%2F2');
    expect(await screen.findByText('workflow wf 1 run')).toBeInTheDocument();
  });

  it('opens its section in the host settings', async () => {
    const router = await open('/spec-queue');
    await act(async () => screen.getByText('queue settings').click());
    expect(router.state.location.pathname).toBe('/project-settings/spec-queue');
  });

  it('goes back to the dashboard', async () => {
    const router = await open('/spec-queue');
    await act(async () => screen.getByText('queue back').click());
    expect(router.state.location.pathname).toBe(DASHBOARD_PATH);
  });
});

describe('workflow', () => {
  it('shows the overview, or a run when the path names one, and goes back', async () => {
    const router = await open('/workflow');
    expect(screen.getByText('workflow overview')).toBeInTheDocument();
    await act(async () => screen.getByText('workflow back').click());
    expect(router.state.location.pathname).toBe(DASHBOARD_PATH);
  });

  it('opens a run directly', async () => {
    await open('/workflow/w1/runs/r1');
    expect(screen.getByText('workflow w1 run')).toBeInTheDocument();
  });
});
