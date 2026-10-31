import { fireEvent, screen, waitFor } from '@testing-library/react';
import { render } from '../../../testUtils/render';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as api from '@openjcockpit/api';
import type { AgentCatalog } from '@openjcockpit/api';
import { withQueryClient } from '../../../testUtils/queryClient';
import { AgentsPanel } from './AgentsPanel';
import { ProviderAgentsTable } from './ProviderAgentsTable';
import { LocalAgentsTree } from './LocalAgentsTree';

vi.mock('@openjcockpit/api', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@openjcockpit/api')>()),
  getAgentCatalog: vi.fn(),
  getProviderAgentCard: vi.fn(),
}));

const providerAgent = {
  id: 'p1',
  name: 'summarizer',
  description: 'Summarizes',
  version: '1.2',
  protocolVersion: '0.3',
  streaming: true,
  url: 'https://prov/a',
  backendUrl: 'https://backend/a',
  cardUrl: 'https://prov/card',
};

function catalog(patch: Partial<AgentCatalog> = {}): AgentCatalog {
  return {
    local: { name: 'Local', status: 'OK', message: null, agents: [] },
    providers: [],
    ...patch,
  } as unknown as AgentCatalog;
}

const renderPanel = () => render(withQueryClient(<AgentsPanel />));

describe('AgentsPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(console, 'warn').mockImplementation(() => {});
  });

  it('shows a loading message first', () => {
    vi.mocked(api.getAgentCatalog).mockReturnValue(new Promise(() => {}));
    renderPanel();
    expect(screen.getByText('Loading agents...')).toBeInTheDocument();
  });

  it('shows an error and retries', async () => {
    vi.mocked(api.getAgentCatalog).mockRejectedValueOnce(new Error('boom'));
    renderPanel();
    expect(await screen.findByText('Failed to load the agent catalog.')).toBeInTheDocument();
    vi.mocked(api.getAgentCatalog).mockResolvedValueOnce(catalog());
    fireEvent.click(screen.getByRole('button', { name: 'Retry' }));
    expect(await screen.findByRole('region', { name: 'Local' })).toBeInTheDocument();
  });

  it('shows empty messages for local and provider sections and refreshes', async () => {
    vi.mocked(api.getAgentCatalog).mockResolvedValue(
      catalog({
        providers: [
          { type: 'litellm', name: 'LiteLLM', status: 'OK', message: 'Connected', agents: [] },
        ],
      } as never),
    );
    renderPanel();
    expect(await screen.findByText('No agents in the /agents folder yet.')).toBeInTheDocument();
    expect(screen.getByText('No agents registered at LiteLLM.')).toBeInTheDocument();
    expect(screen.getByText('Connected')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '⟳ Refresh' }));
    await waitFor(() => expect(api.getAgentCatalog).toHaveBeenCalledTimes(2));
  });

  it('shows the status text and message of a failing provider', async () => {
    vi.mocked(api.getAgentCatalog).mockResolvedValue(
      catalog({
        providers: [
          {
            type: 'litellm',
            name: 'LiteLLM',
            status: 'UNAUTHORIZED',
            message: 'Bad key',
            agents: [],
          },
        ],
      } as never),
    );
    renderPanel();
    expect(await screen.findByText('Access denied')).toBeInTheDocument();
    expect(screen.getByText('Bad key')).toBeInTheDocument();
    expect(screen.queryByText('No agents registered at LiteLLM.')).not.toBeInTheDocument();
  });

  it('renders provider agents in a table', async () => {
    vi.mocked(api.getAgentCatalog).mockResolvedValue(
      catalog({
        providers: [{ type: 'litellm', name: 'LiteLLM', status: 'OK', agents: [providerAgent] }],
      } as never),
    );
    renderPanel();
    expect(await screen.findByText('summarizer')).toBeInTheDocument();
  });
});

describe('ProviderAgentsTable', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(console, 'warn').mockImplementation(() => {});
  });

  const renderTable = (agents = [providerAgent]) =>
    render(
      <ProviderAgentsTable
        provider={{ name: 'LiteLLM', type: 'litellm' }}
        agents={agents as never}
      />,
    );

  it('lists agent details including streaming and backend route', () => {
    renderTable();
    expect(screen.getByText(/0\.3/)).toHaveTextContent('· streaming');
    expect(screen.getByText('routes to https://backend/a')).toBeInTheDocument();
  });

  it('omits streaming and backend route when absent', () => {
    renderTable([{ ...providerAgent, streaming: false, backendUrl: '' }]);
    expect(screen.queryByText(/streaming/)).not.toBeInTheDocument();
    expect(screen.queryByText(/routes to/)).not.toBeInTheDocument();
  });

  it('loads and shows a full agent card, then hides it', async () => {
    vi.mocked(api.getProviderAgentCard).mockResolvedValue({
      name: 'summarizer',
      url: 'https://prov/a',
      protocolVersion: '0.3',
      preferredTransport: 'JSONRPC',
      capabilities: { streaming: true, pushNotifications: false },
      defaultInputModes: ['text'],
      defaultOutputModes: ['text', 'json'],
      skills: [{ id: 's1', name: 'Summarize', description: 'Shortens text' }, { id: 's2' }],
    });
    renderTable();
    const toggle = screen.getByRole('button', { name: 'Details' });
    fireEvent.click(toggle);
    expect(screen.getByRole('status')).toHaveTextContent('Loading agent card...');
    expect(await screen.findByLabelText('Agent card summarizer')).toBeInTheDocument();
    expect(api.getProviderAgentCard).toHaveBeenCalledWith('litellm', 'summarizer');
    expect(screen.getByText(/JSONRPC/)).toBeInTheDocument();
    expect(screen.getByText(/streaming yes · push notifications no/)).toBeInTheDocument();
    expect(screen.getByText('text → text, json')).toBeInTheDocument();
    expect(screen.getByText('Summarize')).toBeInTheDocument();
    expect(screen.getByText('s2')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Hide' }));
    expect(screen.queryByLabelText('Agent card summarizer')).not.toBeInTheDocument();
  });

  it('shows push notifications as supported when the card says so', async () => {
    vi.mocked(api.getProviderAgentCard).mockResolvedValue({
      name: 'summarizer',
      capabilities: { streaming: false, pushNotifications: true },
    });
    renderTable();
    fireEvent.click(screen.getByRole('button', { name: 'Details' }));
    expect(await screen.findByText(/streaming no · push notifications yes/)).toBeInTheDocument();
  });

  it('copes with a minimal agent card', async () => {
    vi.mocked(api.getProviderAgentCard).mockResolvedValue({ name: 'summarizer' });
    renderTable();
    fireEvent.click(screen.getByRole('button', { name: 'Details' }));
    expect(await screen.findByText(/streaming no · push notifications no/)).toBeInTheDocument();
  });

  it('shows an error when the card cannot be retrieved', async () => {
    vi.mocked(api.getProviderAgentCard).mockRejectedValue(new Error('down'));
    renderTable();
    fireEvent.click(screen.getByRole('button', { name: 'Details' }));
    expect(
      await screen.findByText('Could not retrieve the agent card from LiteLLM.'),
    ).toBeInTheDocument();
  });
});

describe('LocalAgentsTree', () => {
  const agent = (id: string, folders: string[]) => ({
    id,
    name: id,
    path: `${folders.join('/')}/${id}.md`,
    folders,
    skills: [],
    tools: [],
    valid: true,
  });

  it('renders sibling folders in alphabetical order, nested folders and root agents', () => {
    render(
      <LocalAgentsTree
        agents={[
          agent('zed', ['zeta']),
          agent('amy', ['alpha']),
          agent('deep', ['alpha', 'inner']),
          agent('rooty', []),
        ]}
      />,
    );
    const headings = screen.getAllByText(/📁/).map((h) => h.textContent);
    expect(headings).toEqual(['📁 alpha', '📁 inner', '📁 zeta']);
    expect(screen.getByText('rooty')).toBeInTheDocument();
    expect(screen.getByText('deep')).toBeInTheDocument();
  });
});
