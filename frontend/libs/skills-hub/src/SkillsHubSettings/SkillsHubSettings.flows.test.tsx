import { fireEvent, screen, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as api from '@openjcockpit/api';
import type { SkillsMarketplaceConnection } from '@openjcockpit/api';
import { SkillsHubSettings } from './SkillsHubSettings';
import { clickAsync, renderAsync } from '../testUtils';

vi.mock('@openjcockpit/api', async (importOriginal) =>
  (await import('../testUtils')).automockApiClients(await importOriginal()),
);

function connection(
  overrides: Partial<SkillsMarketplaceConnection> = {},
): SkillsMarketplaceConnection {
  return {
    id: 'mp-1',
    name: 'Acme Skills',
    marketplaceUrl: 'https://marketplace.example.com/api',
    description: 'Internal catalog',
    enabled: true,
    hasApiKey: true,
    createdBy: 'ricky',
    createdAt: '2026-01-05T10:00:00Z',
    updatedAt: '2026-01-05T10:00:00Z',
    ...overrides,
  };
}

async function open(connections: SkillsMarketplaceConnection[]) {
  vi.mocked(api.listSkillsMarketplaces).mockResolvedValue(connections);
  const onBack = vi.fn();
  await renderAsync(<SkillsHubSettings onBack={onBack} />);
  await screen.findByText('Marketplace connections');
  return { onBack };
}

function type(label: string, value: string) {
  fireEvent.change(screen.getByLabelText(label), { target: { value } });
}

function row(name: string) {
  return screen.getByText(name).closest('tr') as HTMLElement;
}

describe('SkillsHubSettings — flows', () => {
  beforeEach(() => vi.clearAllMocks());

  it('shows a loading message while the list is pending', async () => {
    vi.mocked(api.listSkillsMarketplaces).mockReturnValue(new Promise(() => {}));
    await renderAsync(<SkillsHubSettings onBack={vi.fn()} />);
    expect(screen.getByText('Loading marketplaces…')).toBeInTheDocument();
  });

  it('uses a generic message for a non-Error load failure', async () => {
    vi.mocked(api.listSkillsMarketplaces).mockRejectedValue('nope');
    await renderAsync(<SkillsHubSettings onBack={vi.fn()} />);
    expect(await screen.findByRole('alert')).toHaveTextContent('Failed to load marketplaces.');
  });

  it('goes back via the header', async () => {
    const { onBack } = await open([]);
    fireEvent.click(screen.getByRole('button', { name: '← Back' }));
    expect(onBack).toHaveBeenCalledTimes(1);
  });

  it('describes each connection in the table', async () => {
    await open([
      connection(),
      connection({ id: 'mp-2', name: 'Bare', description: null, hasApiKey: false, enabled: false }),
    ]);
    const full = row('Acme Skills');
    expect(within(full).getByText('Internal catalog')).toBeInTheDocument();
    expect(within(full).getByText('API key set')).toBeInTheDocument();
    expect(within(full).getByText('Enabled')).toBeInTheDocument();
    expect(within(full).getByText('Jan 5, 2026')).toBeInTheDocument();
    const bare = row('Bare');
    expect(within(bare).getByText('No API key')).toBeInTheDocument();
    expect(within(bare).getByText('Disabled')).toBeInTheDocument();
  });

  it('requires a name before saving', async () => {
    await open([]);
    await clickAsync(screen.getByRole('button', { name: 'Save' }));
    expect(screen.getByRole('alert')).toHaveTextContent('Name is required.');
    expect(api.createSkillsMarketplace).not.toHaveBeenCalled();
  });

  it('requires a marketplace URL before saving', async () => {
    await open([]);
    type('Name *', 'Acme');
    await clickAsync(screen.getByRole('button', { name: 'Save' }));
    expect(screen.getByRole('alert')).toHaveTextContent('Marketplace URL is required.');
    expect(api.createSkillsMarketplace).not.toHaveBeenCalled();
  });

  it('adds new connections in name order and announces the result', async () => {
    await open([connection({ name: 'Zulu' })]);
    vi.mocked(api.createSkillsMarketplace).mockImplementation(async (req) =>
      connection({ id: 'mp-9', name: req.name }),
    );
    type('Name *', 'Alpha');
    type('Marketplace URL *', 'https://a.example.com');
    type('API key *', 'k');
    type('Description', 'desc');
    fireEvent.click(screen.getByRole('checkbox'));
    await clickAsync(screen.getByRole('button', { name: 'Save' }));

    expect(api.createSkillsMarketplace).toHaveBeenCalledWith({
      name: 'Alpha',
      marketplaceUrl: 'https://a.example.com',
      apiKey: 'k',
      description: 'desc',
      enabled: false,
    });
    expect(screen.getByRole('status')).toHaveTextContent('Marketplace "Alpha" has been added.');
    const names = screen.getAllByRole('row').map((r) => r.textContent ?? '');
    expect(names.findIndex((t) => t.includes('Alpha'))).toBeLessThan(
      names.findIndex((t) => t.includes('Zulu')),
    );
    expect(screen.getByLabelText('Name *')).toHaveValue('');
  });

  it('prefills the form on edit and updates the row', async () => {
    await open([connection()]);
    vi.mocked(api.updateSkillsMarketplace).mockImplementation(async (id, req) =>
      connection({ id, name: req.name }),
    );
    await clickAsync(within(row('Acme Skills')).getByText('Edit'));

    expect(screen.getByText('Edit marketplace')).toBeInTheDocument();
    expect(screen.getByLabelText('Name *')).toHaveValue('Acme Skills');
    expect(screen.getByLabelText('API key')).toHaveValue('');
    expect(screen.getByLabelText('Description')).toHaveValue('Internal catalog');

    type('Name *', 'Acme 2');
    await clickAsync(screen.getByRole('button', { name: 'Save' }));

    expect(api.updateSkillsMarketplace).toHaveBeenCalledWith(
      'mp-1',
      expect.objectContaining({ name: 'Acme 2', apiKey: '' }),
    );
    expect(screen.getByRole('status')).toHaveTextContent('Marketplace "Acme 2" has been updated.');
    expect(screen.getByText('New marketplace')).toBeInTheDocument();
  });

  it('leaves other connections untouched when updating one', async () => {
    await open([connection(), connection({ id: 'mp-2', name: 'Zulu' })]);
    vi.mocked(api.updateSkillsMarketplace).mockImplementation(async (id, req) =>
      connection({ id, name: req.name }),
    );
    await clickAsync(within(row('Acme Skills')).getByText('Edit'));
    type('Name *', 'Acme 2');
    await clickAsync(screen.getByRole('button', { name: 'Save' }));

    expect(screen.getByText('Acme 2')).toBeInTheDocument();
    expect(screen.getByText('Zulu')).toBeInTheDocument();
  });

  it('edits a connection that has no description as an empty field', async () => {
    await open([connection({ description: null })]);
    await clickAsync(within(row('Acme Skills')).getByText('Edit'));
    expect(screen.getByLabelText('Description')).toHaveValue('');
  });

  it('can still add a marketplace after the list failed to load', async () => {
    vi.mocked(api.listSkillsMarketplaces).mockRejectedValue(new Error('down'));
    vi.mocked(api.createSkillsMarketplace).mockImplementation(async (req) =>
      connection({ id: 'mp-9', name: req.name }),
    );
    await renderAsync(<SkillsHubSettings onBack={vi.fn()} />);
    await screen.findByRole('alert');
    type('Name *', 'Alpha');
    type('Marketplace URL *', 'https://a.example.com');
    type('API key *', 'k');
    await clickAsync(screen.getByRole('button', { name: 'Save' }));

    expect(screen.getByRole('status')).toHaveTextContent('Marketplace "Alpha" has been added.');
    expect(screen.getByText('Alpha')).toBeInTheDocument();
  });

  it('asks for a key on edit when the connection has none', async () => {
    await open([connection({ hasApiKey: false })]);
    await clickAsync(within(row('Acme Skills')).getByText('Edit'));
    expect(screen.getByPlaceholderText('Required — no API key set yet')).toBeInTheDocument();
  });

  it('cancels an edit and resets the form', async () => {
    await open([connection()]);
    await clickAsync(within(row('Acme Skills')).getByText('Edit'));
    await clickAsync(screen.getByRole('button', { name: 'Cancel' }));
    expect(screen.getByText('New marketplace')).toBeInTheDocument();
    expect(screen.getByLabelText('Name *')).toHaveValue('');
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('focuses the name field after starting an edit', async () => {
    await open([connection()]);
    await clickAsync(within(row('Acme Skills')).getByText('Edit'));
    expect(screen.getByLabelText('Name *')).toHaveFocus();
  });

  it('shows the server message when saving fails, and a fallback for non-Errors', async () => {
    await open([]);
    type('Name *', 'A');
    type('Marketplace URL *', 'u');
    vi.mocked(api.createSkillsMarketplace).mockRejectedValueOnce(new Error('duplicate url'));
    await clickAsync(screen.getByRole('button', { name: 'Save' }));
    expect(screen.getByRole('alert')).toHaveTextContent('duplicate url');
    expect(screen.getByLabelText('Name *')).toHaveValue('A');

    vi.mocked(api.createSkillsMarketplace).mockRejectedValueOnce('boom');
    await clickAsync(screen.getByRole('button', { name: 'Save' }));
    expect(screen.getByRole('alert')).toHaveTextContent('Failed to save the marketplace.');
  });

  it('resets the form when the connection being edited is deleted', async () => {
    await open([connection()]);
    vi.mocked(api.deleteSkillsMarketplace).mockResolvedValue(undefined);
    await clickAsync(within(row('Acme Skills')).getByText('Edit'));
    await clickAsync(within(row('Acme Skills')).getByText('Delete'));
    await clickAsync(screen.getByTestId('confirm-action-confirm'));
    expect(screen.getByText('New marketplace')).toBeInTheDocument();
    expect(screen.queryByText('Acme Skills', { selector: 'strong' })).not.toBeInTheDocument();
  });

  it('keeps the form when a different connection is deleted', async () => {
    await open([connection(), connection({ id: 'mp-2', name: 'Other' })]);
    vi.mocked(api.deleteSkillsMarketplace).mockResolvedValue(undefined);
    await clickAsync(within(row('Acme Skills')).getByText('Edit'));
    await clickAsync(within(row('Other')).getByText('Delete'));
    await clickAsync(screen.getByTestId('confirm-action-confirm'));
    expect(screen.getByText('Edit marketplace')).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('Marketplace "Other" has been deleted.');
  });

  it('falls back to a generic message when deleting fails with a non-Error', async () => {
    await open([connection()]);
    vi.mocked(api.deleteSkillsMarketplace).mockRejectedValue('boom');
    await clickAsync(screen.getByRole('button', { name: 'Delete' }));
    await clickAsync(screen.getByTestId('confirm-action-confirm'));
    expect(screen.getByRole('alert')).toHaveTextContent('Failed to delete the marketplace.');
    expect(screen.getByText('Acme Skills')).toBeInTheDocument();
  });
});
