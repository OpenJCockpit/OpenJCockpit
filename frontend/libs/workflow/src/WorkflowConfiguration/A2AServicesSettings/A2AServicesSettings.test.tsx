import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { withQueryClient } from '../../testUtils/queryClient';
import { A2AServicesSettings } from './A2AServicesSettings';
import * as workflowApi from '@openjcockpit/api';
import type { A2AService } from '@openjcockpit/api';

vi.mock('@openjcockpit/api', async (importOriginal) =>
  (await import('../../testUtils/mockApi')).automockApiClients(await importOriginal()),
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
    render(withQueryClient(<A2AServicesSettings />));
  });
  // The query resolves on react-query's own scheduler, after act has returned.
  await waitFor(() => expect(screen.queryByText(/Loading A2A services/)).not.toBeInTheDocument());
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
    const litellm = within(rows[1]!);
    expect(litellm.getByText('LiteLLM')).toBeInTheDocument();
    expect(litellm.getByText('Reachable · 4 agents')).toBeInTheDocument();
    expect(litellm.getByText('Read-only')).toBeInTheDocument();
    expect(litellm.getByText('Caller identity: openjcockpit-a2a')).toBeInTheDocument();
    expect(litellm.queryByRole('button')).not.toBeInTheDocument();

    const added = within(rows[2]!);
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

  it('falls back to a generic message when loading fails with a non-Error', async () => {
    vi.mocked(workflowApi.listA2AServices).mockRejectedValue('boom');
    await renderSettings();
    expect(screen.getByRole('alert')).toHaveTextContent('Failed to load A2A services.');
  });

  it('shows the empty state when the API returns no services', async () => {
    vi.mocked(workflowApi.listA2AServices).mockResolvedValue(undefined as unknown as A2AService[]);
    await renderSettings();
    expect(screen.getByText('No A2A services configured yet.')).toBeInTheDocument();
  });

  it('validates each required field when adding', async () => {
    await renderSettings();
    fireEvent.click(screen.getByRole('button', { name: '+ Add A2A service' }));
    const add = () => fireEvent.click(screen.getByRole('button', { name: 'Add A2A service' }));

    add();
    expect(screen.getByRole('alert')).toHaveTextContent('Id is required.');
    fireEvent.change(input(/^Id/), { target: { value: 'beta' } });
    add();
    expect(screen.getByRole('alert')).toHaveTextContent('Name is required.');
    fireEvent.change(input(/^Name/), { target: { value: 'Beta' } });
    add();
    expect(screen.getByRole('alert')).toHaveTextContent('Base URL is required.');
    expect(workflowApi.createA2AService).not.toHaveBeenCalled();
  });

  it('sends trimmed optional caller identity fields and the public URL', async () => {
    vi.mocked(workflowApi.createA2AService).mockResolvedValue(service({ ...acme, id: 'beta' }));
    await renderSettings();
    fireEvent.click(screen.getByRole('button', { name: '+ Add A2A service' }));
    fireEvent.change(input(/^Id/), { target: { value: 'beta' } });
    fireEvent.change(input(/^Name/), { target: { value: 'Beta' } });
    fireEvent.change(input(/^Base URL/), { target: { value: 'https://beta.example' } });
    fireEvent.change(input(/^Public URL/), { target: { value: ' https://pub.example ' } });
    fireEvent.change(input(/^API key/), { target: { value: 'sk' } });
    fireEvent.change(input(/^Caller token URL/), { target: { value: 'https://idp/token' } });
    fireEvent.change(input(/^Caller client id/), { target: { value: 'client' } });
    fireEvent.change(input(/^Caller client secret/), { target: { value: 'shh' } });
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Add A2A service' }));
    });

    expect(workflowApi.createA2AService).toHaveBeenCalledWith({
      id: 'beta',
      name: 'Beta',
      baseUrl: 'https://beta.example',
      publicUrl: 'https://pub.example',
      apiKey: 'sk',
      callerTokenUrl: 'https://idp/token',
      callerClientId: 'client',
      callerClientSecret: 'shh',
    });
  });

  it('shows the saving state, then the error when saving fails, and cancels the form', async () => {
    let reject: (e: unknown) => void = () => undefined;
    vi.mocked(workflowApi.updateA2AService).mockReturnValue(
      new Promise((_, r) => {
        reject = r;
      }),
    );
    await renderSettings();
    fireEvent.click(screen.getByRole('button', { name: 'Edit' }));
    expect(screen.getByRole('heading', { name: 'Edit Acme gateway' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Save A2A service' }));
    expect(screen.getByRole('button', { name: /Saving/ })).toBeDisabled();
    await act(async () => reject('nope'));
    expect(screen.getByRole('alert')).toHaveTextContent('Failed to save A2A service.');

    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(screen.queryByRole('heading', { name: /Edit Acme/ })).not.toBeInTheDocument();
  });

  it('shows the error message when adding fails', async () => {
    vi.mocked(workflowApi.createA2AService).mockRejectedValue(new Error('Id already exists'));
    await renderSettings();
    fireEvent.click(screen.getByRole('button', { name: '+ Add A2A service' }));
    fireEvent.change(input(/^Id/), { target: { value: 'beta' } });
    fireEvent.change(input(/^Name/), { target: { value: 'Beta' } });
    fireEvent.change(input(/^Base URL/), { target: { value: 'https://beta.example' } });
    fireEvent.change(input(/^API key/), { target: { value: 'sk' } });
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Add A2A service' }));
    });
    expect(screen.getByRole('alert')).toHaveTextContent('Id already exists');
  });

  it('keeps a service when removal is declined and closes the edit form when it is removed', async () => {
    vi.mocked(workflowApi.deleteA2AService).mockResolvedValue(undefined);
    await renderSettings();

    fireEvent.click(screen.getByRole('button', { name: 'Remove' }));
    fireEvent.click(screen.getByRole('button', { name: 'Keep' }));
    expect(screen.getByRole('button', { name: 'Remove' })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Edit' }));
    fireEvent.click(screen.getByRole('button', { name: 'Remove' }));
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Confirm remove' }));
    });
    expect(screen.getByText('A2A service "Acme gateway" removed.')).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: /Edit Acme/ })).not.toBeInTheDocument();
  });

  it('falls back to a generic message when removal fails with a non-Error', async () => {
    vi.mocked(workflowApi.deleteA2AService).mockRejectedValue('x');
    await renderSettings();
    fireEvent.click(screen.getByRole('button', { name: 'Remove' }));
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Confirm remove' }));
    });
    expect(screen.getByRole('alert')).toHaveTextContent('Failed to remove A2A service.');
  });

  it('shows the public URL when it differs and prefills the edit form from the service', async () => {
    vi.mocked(workflowApi.listA2AServices).mockResolvedValue([
      {
        ...acme,
        publicUrl: 'https://public.acme.example',
        callerClientId: 'cid',
        hasApiKey: false,
      },
    ]);
    await renderSettings();
    expect(screen.getByText('public: https://public.acme.example')).toBeInTheDocument();
    expect(screen.getByText('No API key')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Edit' }));
    expect(input(/^Public URL/).value).toBe('https://public.acme.example');
    expect(input(/^Caller client id/).value).toBe('cid');
  });

  it('prefills an empty base URL when the service has none', async () => {
    vi.mocked(workflowApi.listA2AServices).mockResolvedValue([{ ...acme, baseUrl: null }]);
    await renderSettings();
    fireEvent.click(screen.getByRole('button', { name: 'Edit' }));
    expect(input(/^Base URL/).value).toBe('');
  });
});
