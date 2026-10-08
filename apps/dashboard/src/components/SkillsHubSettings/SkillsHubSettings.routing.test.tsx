import { act, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import App from '../../App';
import * as api from '../../api';
import * as workflowApi from '../../workflow/workflowApi';
import { mockWorkspace } from '../../mockWorkspace';
import type { Project } from '../../types';

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

vi.mock('../../auth/keycloak', () => ({
  getKeycloak: () => ({
    init: () => Promise.resolve(true),
    token: 'fake-token',
    updateToken: () => Promise.resolve(false),
    login: vi.fn(),
  }),
}));

vi.mock('../../api', async () => {
  const actual = await vi.importActual<typeof import('../../api')>('../../api');
  return {
    ...actual,
    loadProjects: vi.fn(),
    loadWorkspace: vi.fn(),
    loadAgentDefinitions: vi.fn(),
    loadProjectSpecs: vi.fn(),
    initSpecFolder: vi.fn(),
    getSpecInitStatus: vi.fn(),
    checkWorkflowPreflight: vi.fn(),
    getAgentRun: vi.fn(),
    stopAgentRun: vi.fn(),
    listSkillsMarketplaces: vi.fn(),
  };
});

vi.mock('../../workflow/workflowApi', async () => {
  const actual = await vi.importActual<typeof import('../../workflow/workflowApi')>(
    '../../workflow/workflowApi',
  );
  return {
    ...actual,
    listWorkflows: vi.fn(),
    listWorkflowGroups: vi.fn(),
    startWorkflow: vi.fn(),
  };
});

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
    localStorage.setItem('metafactory_project_id', PROJECT.id);
    vi.mocked(api.loadProjects).mockResolvedValue([PROJECT]);
    vi.mocked(api.loadWorkspace).mockResolvedValue(mockWorkspace);
    vi.mocked(api.loadAgentDefinitions).mockResolvedValue([]);
    vi.mocked(api.loadProjectSpecs).mockResolvedValue(mockWorkspace.specs);
    vi.mocked(api.listSkillsMarketplaces).mockResolvedValue([]);
    vi.mocked(workflowApi.listWorkflows).mockResolvedValue([]);
    vi.mocked(workflowApi.listWorkflowGroups).mockResolvedValue([]);

    await act(async () => {
      render(<App />);
    });
    await screen.findByText('🧩 Skills Hub');

    await act(async () => {
      screen.getByText('🧩 Skills Hub').click();
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
    expect(screen.queryByText('🧩 Skills Hub')).not.toBeInTheDocument();
  });
});
