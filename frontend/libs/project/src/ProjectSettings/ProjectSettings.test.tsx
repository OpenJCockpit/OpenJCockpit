import { screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ProjectSettings } from './ProjectSettings';
import * as api from '@openjcockpit/api';
import type { Project } from '@openjcockpit/api';
import { clickAsync, renderAsync } from '../testUtils';

vi.mock('@openjcockpit/api', async (importOriginal) =>
  (await import('../testUtils')).automockApiClients(await importOriginal()),
);

const PROJECT: Project = {
  id: 'project-1',
  name: 'Absence Pro Solutions',
  customerId: 'absence-pro',
  gitUrl: 'https://github.com/org/absence-pro',
  active: 1,
  newProject: 0,
  gitStatus: 'UNKNOWN',
  hasCredentials: true,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
};

async function renderSettings() {
  await renderAsync(<ProjectSettings onBack={vi.fn()} />);
  await screen.findByText('Absence Pro Solutions');
}

async function openCredentialsForm() {
  await renderSettings();
  await clickAsync(screen.getByText('Update credentials'));
}

describe('ProjectSettings — Git credentials', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(api.loadAllProjects).mockResolvedValue([PROJECT]);
  });

  it('populates the form with the existing credential when "Update credentials" is clicked', async () => {
    vi.mocked(api.getGitCredentials).mockResolvedValue({
      credentialType: 'GITHUB_PAT',
      username: 'ci-bot',
      githubApiUrl: 'https://github.example.com/api/v3',
      hasSecret: true,
    });

    await openCredentialsForm();

    await waitFor(() => expect(api.getGitCredentials).toHaveBeenCalledWith('project-1'));
    expect(await screen.findByDisplayValue('ci-bot')).toBeInTheDocument();
    expect(screen.getByDisplayValue('https://github.example.com/api/v3')).toBeInTheDocument();
    expect(screen.getByRole('combobox', { name: /Type/ })).toHaveTextContent('GitHub PAT');
    // secret is never sent back from the server — the field starts blank with a hint that one is set
    expect(screen.getByPlaceholderText('Leave empty to keep the current secret')).toHaveValue('');
  });

  it('shows a "required" placeholder for the secret field when no credential exists yet', async () => {
    vi.mocked(api.getGitCredentials).mockResolvedValue({
      credentialType: 'NONE',
      hasSecret: false,
    });

    await openCredentialsForm();

    expect(await screen.findByPlaceholderText('Required — no secret set yet')).toBeInTheDocument();
  });

  it('shows the backend error message when saving fails validation', async () => {
    vi.mocked(api.getGitCredentials).mockResolvedValue({
      credentialType: 'NONE',
      hasSecret: false,
    });
    vi.mocked(api.saveGitCredentials).mockRejectedValue(
      new Error('A secret/token is required for credential type GITHUB_PAT'),
    );

    await openCredentialsForm();
    await screen.findByText('Type');

    await clickAsync(screen.getByText('Save'));

    expect(
      await screen.findByText('A secret/token is required for credential type GITHUB_PAT'),
    ).toBeInTheDocument();
  });

  it('shows the backend error message when the pre-save git check fails', async () => {
    vi.mocked(api.getGitCredentials).mockResolvedValue({
      credentialType: 'NONE',
      hasSecret: false,
    });
    vi.mocked(api.saveGitCredentials).mockRejectedValue(
      new Error('Git credentials could not be verified: Authentication failed'),
    );

    await openCredentialsForm();
    await screen.findByText('Type');

    await clickAsync(screen.getByText('Save'));

    expect(
      await screen.findByText('Git credentials could not be verified: Authentication failed'),
    ).toBeInTheDocument();
  });

  it('marks the project as having credentials after a successful save', async () => {
    vi.mocked(api.getGitCredentials).mockResolvedValue({
      credentialType: 'NONE',
      hasSecret: false,
    });
    vi.mocked(api.saveGitCredentials).mockResolvedValue(undefined);

    await openCredentialsForm();
    await screen.findByText('Type');

    await clickAsync(screen.getByText('Save'));

    await waitFor(() =>
      expect(api.saveGitCredentials).toHaveBeenCalledWith('project-1', expect.any(Object)),
    );
    expect(screen.getByText('Credentials set')).toBeInTheDocument();
  });

  it('shows a load error and does not crash when fetching existing credentials fails', async () => {
    vi.mocked(api.getGitCredentials).mockRejectedValue(new Error('network error'));

    await openCredentialsForm();

    expect(await screen.findByText('Failed to load existing credentials.')).toBeInTheDocument();
  });
});

describe('ProjectSettings — name validation', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(api.loadAllProjects).mockResolvedValue([PROJECT]);
  });

  it('renders the required-name error as an alert associated with the name input via aria-describedby', async () => {
    await renderSettings();

    await clickAsync(screen.getByText('+ New project'));
    const nameInput = await screen.findByPlaceholderText('Project name');

    await clickAsync(screen.getByText('Save'));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Name is required');
    expect(nameInput).toHaveAttribute('aria-describedby', alert.id);
    expect(nameInput).toHaveAttribute('aria-invalid', 'true');
  });

  it('moves focus to the name input when saving fails required-name validation', async () => {
    await renderSettings();

    await clickAsync(screen.getByText('+ New project'));
    const nameInput = await screen.findByPlaceholderText('Project name');

    await clickAsync(screen.getByText('Save'));

    await waitFor(() => expect(document.activeElement).toBe(nameInput));
  });
});
