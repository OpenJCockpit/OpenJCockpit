import { act, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { renderRoutedApp } from './testUtils/renderRoutedApp';
import * as api from '@openjcockpit/api';
import * as workflowApi from '@openjcockpit/api';
import { mockWorkspace } from '@openjcockpit/api/testing';
import type { Project } from '@openjcockpit/api';
import { enterDashboard } from './testUtils/dom';

// This file renders the full <App>, which (via api.ts) touches `localStorage`.
// It intentionally does NOT rely on any global localStorage shim from
// setupTests.ts (there is none, and it is out of scope for this feature —
// see App.preflight.test.tsx / App.specworkflow.test.tsx, which are red on
// this machine for an unrelated Node-version reason). Instead this file stubs
// an in-memory localStorage locally, scoped to this file only.
function createLocalStorageStub(): Storage {
  let store: Record<string, string> = {};
  return {
    getItem: (key: string) => (key in store ? store[key] : null),
    setItem: (key: string, value: string) => {
      store[key] = String(value);
    },
    removeItem: (key: string) => {
      delete store[key];
    },
    clear: () => {
      store = {};
    },
    key: () => null,
    length: 0,
  };
}

vi.mock(
  '@openjcockpit/landing-hub',
  async () => (await import('./testUtils/landingHubStub')).landingHubStub,
);

vi.mock('@openjcockpit/auth', async () => ({
  ...(await vi.importActual<typeof import('@openjcockpit/auth')>('@openjcockpit/auth')),
  getKeycloak: () => ({
    init: () => Promise.resolve(true),
    token: 'fake-token',
    updateToken: () => Promise.resolve(false),
    login: vi.fn(),
  }),
}));

vi.mock('@openjcockpit/api', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@openjcockpit/api')>()),
  loadProjects: vi.fn(),
  loadWorkspace: vi.fn(),
  loadProjectSpecs: vi.fn(),
  initSpecFolder: vi.fn(),
  getSpecInitStatus: vi.fn(),
  checkWorkflowPreflight: vi.fn(),
  getAgentRun: vi.fn(),
  stopAgentRun: vi.fn(),
  listSkillsMarketplaces: vi.fn(),
  listWorkflows: vi.fn(),
  listWorkflowGroups: vi.fn(),
  startWorkflow: vi.fn(),
}));

const PROJECT: Project = {
  id: 'project-1',
  name: 'Test Project',
  active: 1,
  newProject: 0,
  gitStatus: 'ACCESSIBLE',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
};

describe('Skills Hub Settings — routing (AC-27)', () => {
  beforeEach(() => {
    vi.stubGlobal('localStorage', createLocalStorageStub());
    vi.clearAllMocks();
  });

  it('opens Skills Hub Settings from the Settings control and returns to the Skills Hub (not the dashboard) on back', async () => {
    localStorage.setItem('openjcockpit_project_id', PROJECT.id);
    vi.mocked(api.loadProjects).mockResolvedValue([PROJECT]);
    vi.mocked(api.loadWorkspace).mockResolvedValue(mockWorkspace);
    vi.mocked(api.loadProjectSpecs).mockResolvedValue(mockWorkspace.specs);
    vi.mocked(api.listSkillsMarketplaces).mockResolvedValue([]);
    vi.mocked(workflowApi.listWorkflows).mockResolvedValue([]);
    vi.mocked(workflowApi.listWorkflowGroups).mockResolvedValue([]);

    await act(async () => {
      renderRoutedApp();
    });
    await enterDashboard();
    await screen.findByTestId('nav-skills-hub');

    await act(async () => {
      screen.getByTestId('nav-skills-hub').click();
    });
    await screen.findByText('Available skills');

    await act(async () => {
      screen.getByRole('button', { name: '⚙ Settings' }).click();
    });

    expect(await screen.findByText('Marketplace connections')).toBeInTheDocument();

    await act(async () => {
      screen.getByRole('button', { name: '← Back' }).click();
    });

    // Back from Settings must land on the Skills Hub overview, not the project dashboard.
    expect(await screen.findByText('Available skills')).toBeInTheDocument();
    expect(screen.queryByText('Marketplace connections')).not.toBeInTheDocument();
    expect(screen.getByTestId('nav-skills-hub')).toHaveAttribute('aria-current', 'page');
  });
});
