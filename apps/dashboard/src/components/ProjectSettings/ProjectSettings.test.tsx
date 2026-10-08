import { act, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ProjectSettings } from './ProjectSettings';
import * as api from '../../api';
import type { Project } from '../../types';

vi.mock('../../api');

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

    await act(async () => {
      render(<ProjectSettings onBack={vi.fn()} />);
    });
    await screen.findByText('Absence Pro Solutions');

    await act(async () => {
      screen.getByText('Update credentials').click();
    });

    await waitFor(() => expect(api.getGitCredentials).toHaveBeenCalledWith('project-1'));
    expect(await screen.findByDisplayValue('ci-bot')).toBeInTheDocument();
    expect(screen.getByDisplayValue('https://github.example.com/api/v3')).toBeInTheDocument();
    expect(screen.getByDisplayValue('GitHub PAT')).toBeInTheDocument();
    // secret is never sent back from the server — the field starts blank with a hint that one is set
    expect(screen.getByPlaceholderText('Leave empty to keep the current secret')).toHaveValue('');
  });

  it('shows a "required" placeholder for the secret field when no credential exists yet', async () => {
    vi.mocked(api.getGitCredentials).mockResolvedValue({
      credentialType: 'NONE',
      hasSecret: false,
    });

    await act(async () => {
      render(<ProjectSettings onBack={vi.fn()} />);
    });
    await screen.findByText('Absence Pro Solutions');

    await act(async () => {
      screen.getByText('Update credentials').click();
    });

    expect(
      await screen.findByPlaceholderText('Required — no secret set yet'),
    ).toBeInTheDocument();
  });

  it('shows the backend error message when saving fails validation', async () => {
    vi.mocked(api.getGitCredentials).mockResolvedValue({
      credentialType: 'NONE',
      hasSecret: false,
    });
    vi.mocked(api.saveGitCredentials).mockRejectedValue(
      new Error('A secret/token is required for credential type GITHUB_PAT'),
    );

    await act(async () => {
      render(<ProjectSettings onBack={vi.fn()} />);
    });
    await screen.findByText('Absence Pro Solutions');

    await act(async () => {
      screen.getByText('Update credentials').click();
    });
    await screen.findByText('Type');

    await act(async () => {
      screen.getByText('Save').click();
    });

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

    await act(async () => {
      render(<ProjectSettings onBack={vi.fn()} />);
    });
    await screen.findByText('Absence Pro Solutions');

    await act(async () => {
      screen.getByText('Update credentials').click();
    });
    await screen.findByText('Type');

    await act(async () => {
      screen.getByText('Save').click();
    });

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

    await act(async () => {
      render(<ProjectSettings onBack={vi.fn()} />);
    });
    await screen.findByText('Absence Pro Solutions');

    await act(async () => {
      screen.getByText('Update credentials').click();
    });
    await screen.findByText('Type');

    await act(async () => {
      screen.getByText('Save').click();
    });

    await waitFor(() =>
      expect(api.saveGitCredentials).toHaveBeenCalledWith('project-1', expect.any(Object)),
    );
    expect(screen.getByText('Credentials set')).toBeInTheDocument();
  });

  it('shows a load error and does not crash when fetching existing credentials fails', async () => {
    vi.mocked(api.getGitCredentials).mockRejectedValue(new Error('network error'));

    await act(async () => {
      render(<ProjectSettings onBack={vi.fn()} />);
    });
    await screen.findByText('Absence Pro Solutions');

    await act(async () => {
      screen.getByText('Update credentials').click();
    });

    expect(await screen.findByText('Failed to load existing credentials.')).toBeInTheDocument();
  });
});

describe('ProjectSettings — name validation', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(api.loadAllProjects).mockResolvedValue([PROJECT]);
  });

  it('renders the required-name error as an alert associated with the name input via aria-describedby', async () => {
    await act(async () => {
      render(<ProjectSettings onBack={vi.fn()} />);
    });
    await screen.findByText('Absence Pro Solutions');

    await act(async () => {
      screen.getByText('+ New project').click();
    });
    const nameInput = await screen.findByPlaceholderText('Project name');

    await act(async () => {
      screen.getByText('Save').click();
    });

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Name is required');
    expect(nameInput).toHaveAttribute('aria-describedby', alert.id);
    expect(nameInput).toHaveAttribute('aria-invalid', 'true');
  });

  it('moves focus to the name input when saving fails required-name validation', async () => {
    await act(async () => {
      render(<ProjectSettings onBack={vi.fn()} />);
    });
    await screen.findByText('Absence Pro Solutions');

    await act(async () => {
      screen.getByText('+ New project').click();
    });
    const nameInput = await screen.findByPlaceholderText('Project name');

    await act(async () => {
      screen.getByText('Save').click();
    });

    await waitFor(() => expect(document.activeElement).toBe(nameInput));
  });
});
