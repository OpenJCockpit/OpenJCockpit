import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { A2AServicesSettings } from './A2AServicesSettings';
import * as workflowApi from '@openjcockpit/api';
import type { A2AService } from '@openjcockpit/api';

vi.mock('@openjcockpit/api', async (importOriginal) =>
  (await import('../../../testUtils/mockApi')).automockApiClients(await importOriginal()),
);

function service(overrides: Partial<A2AService> = {}): A2AService {
  return {
    id: 'litellm',
    name: 'LiteLLM',
    type: 'litellm',
    source: 'CONFIGURATION',
    editable: false,
    baseUrl: 'http://litellm:4000',
    publicUrl: 'http://localhost:4100',
    hasApiKey: true,
    callerTokenUrl: 'http://keycloak:8080/token',
    callerClientId: 'openjcockpit-a2a',
    hasCallerClientSecret: true,
    status: 'OK',
    agentCount: 4,
    ...overrides,
  };
}

const acme = service({
  id: 'acme',
  name: 'Acme gateway',
  source: 'DASHBOARD',
  editable: true,
  baseUrl: 'https://gw.acme.example',
  publicUrl: null,
  callerTokenUrl: null,
  callerClientId: null,
  hasCallerClientSecret: false,
  status: 'UNAVAILABLE',
  message: 'Acme gateway: connection refused',
  agentCount: null,
});

async function renderSettings() {
  await act(async () => {
    render(<A2AServicesSettings />);
  });
}

function input(label: RegExp) {
  return screen.getByLabelText<HTMLInputElement>(label);
}

describe('A2AServicesSettings', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(workflowApi.listA2AServices).mockResolvedValue([service(), acme]);
  });

  it('lists configured services read-only and added services with their status', async () => {
    await renderSettings();

    expect(screen.getByRole('heading', { name: 'A2A services' })).toBeInTheDocument();
    const rows = screen.getAllByRole('row');
    const litellm = within(rows[1]);
    expect(litellm.getByText('LiteLLM')).toBeInTheDocument();
    expect(litellm.getByText('Reachable · 4 agents')).toBeInTheDocument();
    expect(litellm.getByText('Read-only')).toBeInTheDocument();
    expect(litellm.getByText('Caller identity: openjcockpit-a2a')).toBeInTheDocument();
    expect(litellm.queryByRole('button')).not.toBeInTheDocument();

    const added = within(rows[2]);
    expect(added.getByText('Unreachable')).toBeInTheDocument();
    expect(added.getByText('Acme gateway: connection refused')).toBeInTheDocument();
    expect(added.getByRole('button', { name: 'Edit' })).toBeInTheDocument();
  });

  it('adds a service and shows it in the list', async () => {
    const created = service({ ...acme, id: 'beta', name: 'Beta', status: 'OK', agentCount: 1 });
    vi.mocked(workflowApi.createA2AService).mockResolvedValue(created);
    await renderSettings();

    fireEvent.click(screen.getByRole('button', { name: '+ Add A2A service' }));
    fireEvent.change(input(/^Id/), { target: { value: 'beta' } });
    fireEvent.change(input(/^Name/), { target: { value: 'Beta' } });
    fireEvent.change(input(/^Base URL/), { target: { value: 'https://beta.example' } });
    fireEvent.change(input(/^API key/), { target: { value: 'sk-beta' } });
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Add A2A service' }));
    });

    expect(workflowApi.createA2AService).toHaveBeenCalledWith({
      id: 'beta',
      name: 'Beta',
      baseUrl: 'https://beta.example',
      publicUrl: undefined,
      apiKey: 'sk-beta',
      callerTokenUrl: undefined,
      callerClientId: undefined,
      callerClientSecret: undefined,
    });
    expect(screen.getByText('A2A service "Beta" added.')).toBeInTheDocument();
    expect(screen.getByText('Reachable · 1 agent')).toBeInTheDocument();
    expect(screen.queryByLabelText(/^Id/)).not.toBeInTheDocument();
  });

  it('requires an API key before adding', async () => {
    await renderSettings();
    fireEvent.click(screen.getByRole('button', { name: '+ Add A2A service' }));
    fireEvent.change(input(/^Id/), { target: { value: 'beta' } });
    fireEvent.change(input(/^Name/), { target: { value: 'Beta' } });
    fireEvent.change(input(/^Base URL/), { target: { value: 'https://beta.example' } });
    fireEvent.click(screen.getByRole('button', { name: 'Add A2A service' }));

    expect(screen.getByRole('alert')).toHaveTextContent('API key is required.');
    expect(workflowApi.createA2AService).not.toHaveBeenCalled();
  });

  it('edits a service without resending secrets and keeps its id fixed', async () => {
    vi.mocked(workflowApi.updateA2AService).mockResolvedValue({ ...acme, name: 'Acme renamed' });
    await renderSettings();

    fireEvent.click(screen.getByRole('button', { name: 'Edit' }));
    expect(input(/^Id/)).toBeDisabled();
    expect(input(/^Id/).value).toBe('acme');
    fireEvent.change(input(/^Name/), { target: { value: 'Acme renamed' } });
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Save A2A service' }));
    });

    expect(workflowApi.updateA2AService).toHaveBeenCalledWith(
      'acme',
      expect.objectContaining({
        name: 'Acme renamed',
        apiKey: undefined,
        callerClientSecret: undefined,
      }),
    );
    expect(screen.getByText('Acme renamed')).toBeInTheDocument();
  });

  it('removes a service only after confirmation and shows why removal failed', async () => {
    vi.mocked(workflowApi.deleteA2AService).mockRejectedValueOnce(
      new Error(
        "A2A service 'acme' is used by workflow wf-1. Remove its agents from that workflow first.",
      ),
    );
    await renderSettings();

    fireEvent.click(screen.getByRole('button', { name: 'Remove' }));
    expect(workflowApi.deleteA2AService).not.toHaveBeenCalled();
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Confirm remove' }));
    });
    expect(screen.getByRole('alert')).toHaveTextContent('used by workflow wf-1');
    expect(screen.getByText('Acme gateway')).toBeInTheDocument();

    vi.mocked(workflowApi.deleteA2AService).mockResolvedValueOnce(undefined);
    fireEvent.click(screen.getByRole('button', { name: 'Remove' }));
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Confirm remove' }));
    });
    await waitFor(() => expect(screen.queryByText('Acme gateway')).not.toBeInTheDocument());
    expect(workflowApi.deleteA2AService).toHaveBeenCalledWith('acme');
  });

  it('shows the error when the services cannot be loaded', async () => {
    vi.mocked(workflowApi.listA2AServices).mockRejectedValue(
      new Error('Agent service unreachable'),
    );
    await renderSettings();
    expect(screen.getByRole('alert')).toHaveTextContent('Agent service unreachable');
  });
});
