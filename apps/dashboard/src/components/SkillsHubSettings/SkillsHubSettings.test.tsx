import { act, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { SkillsHubSettings } from './SkillsHubSettings';
import * as api from '../../api';
import type { SkillsMarketplaceConnection } from '../../types';

vi.mock('../../api');

const CONNECTION: SkillsMarketplaceConnection = {
  id: 'mp-1',
  name: 'Acme Skills',
  marketplaceUrl: 'https://marketplace.example.com/api',
  description: 'Internal catalog',
  enabled: true,
  hasApiKey: true,
  createdBy: 'ricky',
  createdAt: '2026-01-05T10:00:00Z',
  updatedAt: '2026-01-05T10:00:00Z',
};

describe('SkillsHubSettings', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('shows an empty-state row and no error when there are no connections (AC-02)', async () => {
    vi.mocked(api.listSkillsMarketplaces).mockResolvedValue([]);

    await act(async () => {
      render(<SkillsHubSettings onBack={vi.fn()} />);
    });

    expect(await screen.findByText('No marketplaces connected yet.')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('distinguishes a failed load from an empty registry (AC-02 / A12)', async () => {
    vi.mocked(api.listSkillsMarketplaces).mockRejectedValue(
      new Error('Failed to load marketplaces: 500'),
    );

    await act(async () => {
      render(<SkillsHubSettings onBack={vi.fn()} />);
    });

    expect(await screen.findByRole('alert')).toHaveTextContent('Failed to load marketplaces: 500');
    expect(screen.queryByText('No marketplaces connected yet.')).not.toBeInTheDocument();
  });

  it('creates a connection and shows it in the table with the key input cleared (AC-03)', async () => {
    vi.mocked(api.listSkillsMarketplaces).mockResolvedValue([]);
    vi.mocked(api.createSkillsMarketplace).mockResolvedValue(CONNECTION);

    await act(async () => {
      render(<SkillsHubSettings onBack={vi.fn()} />);
    });
    await screen.findByText('No marketplaces connected yet.');

    await act(async () => {
      const nameInput = screen.getByLabelText<HTMLInputElement>('Name *');
      const urlInput = screen.getByLabelText<HTMLInputElement>('Marketplace URL *');
      const keyInput = screen.getByLabelText<HTMLInputElement>('API key *');

      setInputValue(nameInput, 'Acme Skills');
      setInputValue(urlInput, 'https://marketplace.example.com/api');
      setInputValue(keyInput, 'secret-key-value');
    });

    await act(async () => {
      screen.getByText('Save').click();
    });

    await waitFor(() =>
      expect(api.createSkillsMarketplace).toHaveBeenCalledWith({
        name: 'Acme Skills',
        marketplaceUrl: 'https://marketplace.example.com/api',
        apiKey: 'secret-key-value',
        description: '',
        enabled: true,
      }),
    );

    expect(await screen.findByText('Acme Skills')).toBeInTheDocument();
    expect(screen.getByText('https://marketplace.example.com/api')).toBeInTheDocument();
    expect(screen.getByText('API key set')).toBeInTheDocument();
    expect(screen.getByText('Enabled')).toBeInTheDocument();
    expect(screen.getByText('2026-01-05')).toBeInTheDocument();
    expect(screen.getByLabelText<HTMLInputElement>('API key *').value).toBe('');
    expect(screen.getByRole('status')).toHaveTextContent('Acme Skills" has been added.');
  });

  it('renders the server error message on a validation failure (AC-13)', async () => {
    vi.mocked(api.listSkillsMarketplaces).mockResolvedValue([]);
    vi.mocked(api.createSkillsMarketplace).mockRejectedValue(
      new Error('marketplaceUrl: must be an absolute http(s) URL'),
    );

    await act(async () => {
      render(<SkillsHubSettings onBack={vi.fn()} />);
    });
    await screen.findByText('No marketplaces connected yet.');

    await act(async () => {
      setInputValue(screen.getByLabelText('Name *'), 'Acme Skills');
      setInputValue(screen.getByLabelText('Marketplace URL *'), 'ftp://marketplace.example.com');
      setInputValue(screen.getByLabelText('API key *'), 'secret-key-value');
    });

    await act(async () => {
      screen.getByText('Save').click();
    });

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'marketplaceUrl: must be an absolute http(s) URL',
    );
  });

  it('shows the "keep current key" placeholder when editing a connection that already has one (AC-05/AC-22)', async () => {
    vi.mocked(api.listSkillsMarketplaces).mockResolvedValue([CONNECTION]);

    await act(async () => {
      render(<SkillsHubSettings onBack={vi.fn()} />);
    });
    await screen.findByText('Acme Skills');

    await act(async () => {
      screen.getByRole('button', { name: 'Edit' }).click();
    });

    const keyInput = await screen.findByPlaceholderText('Leave empty to keep the current API key');
    expect(keyInput).toHaveValue('');
    expect(keyInput).toHaveAttribute('type', 'password');
    expect(screen.getByLabelText('Name *')).toHaveValue('Acme Skills');
  });

  it('keeps the stored key unchanged when the edit form is submitted with an empty API key (AC-05)', async () => {
    vi.mocked(api.listSkillsMarketplaces).mockResolvedValue([CONNECTION]);
    vi.mocked(api.updateSkillsMarketplace).mockResolvedValue({
      ...CONNECTION,
      marketplaceUrl: 'https://new.example.com',
    });

    await act(async () => {
      render(<SkillsHubSettings onBack={vi.fn()} />);
    });
    await screen.findByText('Acme Skills');

    await act(async () => {
      screen.getByRole('button', { name: 'Edit' }).click();
    });
    await screen.findByPlaceholderText('Leave empty to keep the current API key');

    await act(async () => {
      setInputValue(screen.getByLabelText('Marketplace URL *'), 'https://new.example.com');
    });

    await act(async () => {
      screen.getByText('Save').click();
    });

    await waitFor(() =>
      expect(api.updateSkillsMarketplace).toHaveBeenCalledWith(
        'mp-1',
        expect.objectContaining({ apiKey: '' }),
      ),
    );
    expect(await screen.findByText('https://new.example.com')).toBeInTheDocument();
  });

  it('re-encrypts when a new API key value is submitted on edit (AC-06)', async () => {
    vi.mocked(api.listSkillsMarketplaces).mockResolvedValue([CONNECTION]);
    vi.mocked(api.updateSkillsMarketplace).mockResolvedValue(CONNECTION);

    await act(async () => {
      render(<SkillsHubSettings onBack={vi.fn()} />);
    });
    await screen.findByText('Acme Skills');

    await act(async () => {
      screen.getByRole('button', { name: 'Edit' }).click();
    });
    await screen.findByPlaceholderText('Leave empty to keep the current API key');

    await act(async () => {
      setInputValue(screen.getByLabelText(/^API key/), 'brand-new-key');
    });

    await act(async () => {
      screen.getByText('Save').click();
    });

    await waitFor(() =>
      expect(api.updateSkillsMarketplace).toHaveBeenCalledWith(
        'mp-1',
        expect.objectContaining({ apiKey: 'brand-new-key' }),
      ),
    );
  });

  it('removes the row and announces the result on delete', async () => {
    vi.mocked(api.listSkillsMarketplaces).mockResolvedValue([CONNECTION]);
    vi.mocked(api.deleteSkillsMarketplace).mockResolvedValue(undefined);

    await act(async () => {
      render(<SkillsHubSettings onBack={vi.fn()} />);
    });
    await screen.findByText('Acme Skills');

    await act(async () => {
      screen.getByRole('button', { name: 'Delete' }).click();
    });

    await waitFor(() => expect(api.deleteSkillsMarketplace).toHaveBeenCalledWith('mp-1'));
    expect(screen.queryByText('Acme Skills')).not.toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('Acme Skills" has been deleted.');
  });

  it('shows the server error message when delete fails (AC-13)', async () => {
    vi.mocked(api.listSkillsMarketplaces).mockResolvedValue([CONNECTION]);
    vi.mocked(api.deleteSkillsMarketplace).mockRejectedValue(
      new Error('Marketplace not found'),
    );

    await act(async () => {
      render(<SkillsHubSettings onBack={vi.fn()} />);
    });
    await screen.findByText('Acme Skills');

    await act(async () => {
      screen.getByRole('button', { name: 'Delete' }).click();
    });

    expect(await screen.findByRole('alert')).toHaveTextContent('Marketplace not found');
  });

  it('exposes accessible form controls, a semantic table, and a live region (AC-22/23/24/25)', async () => {
    vi.mocked(api.listSkillsMarketplaces).mockResolvedValue([CONNECTION]);

    await act(async () => {
      render(<SkillsHubSettings onBack={vi.fn()} />);
    });
    await screen.findByText('Acme Skills');

    expect(screen.getByLabelText('Name *')).toBeInTheDocument();
    expect(screen.getByLabelText('Marketplace URL *')).toBeInTheDocument();
    const keyInput = screen.getByLabelText(/^API key/);
    expect(keyInput).toHaveAttribute('type', 'password');

    const columnHeaders = screen.getAllByRole('columnheader').map((h) => h.textContent);
    expect(columnHeaders).toEqual([
      'Name',
      'Marketplace URL',
      'API key',
      'Status',
      'Created',
      'Actions',
    ]);

    expect(screen.getByRole('button', { name: 'Edit' }).tagName).toBe('BUTTON');
    expect(screen.getByRole('button', { name: 'Delete' }).tagName).toBe('BUTTON');
    expect(screen.getByRole('button', { name: 'Edit' })).not.toHaveAttribute('href');

    expect(screen.getByRole('status')).toBeInTheDocument();
  });
});

function setInputValue(element: HTMLInputElement | HTMLTextAreaElement, value: string) {
  const prototype =
    element instanceof HTMLTextAreaElement
      ? HTMLTextAreaElement.prototype
      : HTMLInputElement.prototype;
  const setter = Object.getOwnPropertyDescriptor(prototype, 'value')?.set;
  setter?.call(element, value);
  element.dispatchEvent(new Event('input', { bubbles: true }));
}
