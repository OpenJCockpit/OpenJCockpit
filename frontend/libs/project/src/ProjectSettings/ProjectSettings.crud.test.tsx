import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { chooseOption } from '@openjcockpit/ui/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ProjectSettings } from './ProjectSettings';
import * as api from '@openjcockpit/api';
import type { Project } from '@openjcockpit/api';
import { clickAsync, renderAsync } from '../testUtils';

vi.mock('@openjcockpit/api', async (importOriginal) =>
  (await import('../testUtils')).automockApiClients(await importOriginal()),
);

function project(overrides: Partial<Project> = {}): Project {
  return {
    id: 'p1',
    name: 'Beta',
    customerId: 'cust',
    gitUrl: 'https://github.com/org/beta',
    description: 'Beta project',
    defaultBranch: 'develop',
    environment: 'prod',
    owner: 'team-b',
    active: 1,
    newProject: 0,
    gitStatus: 'UNKNOWN',
    hasCredentials: false,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

function row(name: string) {
  return screen.getByText(name).closest('tr') as HTMLElement;
}

async function renderSettings(projects: Project[] = [project()]) {
  vi.mocked(api.loadAllProjects).mockResolvedValue(projects);
  const onBack = vi.fn();
  await renderAsync(<ProjectSettings onBack={onBack} />);
  await screen.findByText('Project management');
  return { onBack };
}

describe('ProjectSettings — loading', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(console, 'error').mockImplementation(() => {});
  });

  it('keeps showing the loading message while the load is pending', async () => {
    vi.mocked(api.loadAllProjects).mockReturnValue(new Promise(() => {}));
    await renderAsync(<ProjectSettings onBack={vi.fn()} />);
    expect(screen.getByText('Loading projects…')).toBeInTheDocument();
  });

  it('keeps the loading message and logs when the load fails', async () => {
    vi.mocked(api.loadAllProjects).mockRejectedValue(new Error('down'));
    await renderAsync(<ProjectSettings onBack={vi.fn()} />);
    await waitFor(() => expect(console.error).toHaveBeenCalled());
    expect(screen.getByText('Loading projects…')).toBeInTheDocument();
  });

  it('goes back via the header button', async () => {
    const { onBack } = await renderSettings();
    fireEvent.click(screen.getByText('← Projects'));
    expect(onBack).toHaveBeenCalledTimes(1);
  });
});

describe('ProjectSettings — create and edit', () => {
  beforeEach(() => vi.clearAllMocks());

  it('creates a project, sorts it into the list and closes the form', async () => {
    await renderSettings([project({ id: 'p1', name: 'Zulu' })]);
    vi.mocked(api.createProject).mockImplementation(async (req) =>
      project({ id: 'p2', name: req.name }),
    );

    await clickAsync(screen.getByText('+ New project'));
    expect(screen.getByText('New project')).toBeInTheDocument();
    fireEvent.change(screen.getByPlaceholderText('Project name'), { target: { value: 'Alpha' } });
    fireEvent.change(screen.getByPlaceholderText('Name or team'), { target: { value: 'me' } });
    fireEvent.click(screen.getByRole('checkbox'));
    await clickAsync(screen.getByText('Save'));

    expect(api.createProject).toHaveBeenCalledWith(
      expect.objectContaining({ name: 'Alpha', owner: 'me', newProject: 1, defaultBranch: 'main' }),
    );
    await waitFor(() => expect(screen.queryByText('New project')).not.toBeInTheDocument());
    const names = screen.getAllByRole('row').map((r) => r.textContent);
    expect(names.findIndex((t) => t?.includes('Alpha'))).toBeLessThan(
      names.findIndex((t) => t?.includes('Zulu')),
    );
  });

  it('shows a save error and keeps the form open when create fails', async () => {
    await renderSettings();
    vi.mocked(api.createProject).mockRejectedValue(new Error('500'));

    await clickAsync(screen.getByText('+ New project'));
    fireEvent.change(screen.getByPlaceholderText('Project name'), { target: { value: 'X' } });
    await clickAsync(screen.getByText('Save'));

    expect(await screen.findByRole('alert')).toHaveTextContent('Failed to save the project.');
    expect(screen.getByText('New project')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('Project name')).not.toHaveAttribute('aria-invalid');
  });

  it('prefills the form from the project and saves the update', async () => {
    await renderSettings();
    vi.mocked(api.updateProject).mockImplementation(async (id, req) =>
      project({ id, name: req.name }),
    );

    await clickAsync(within(row('Beta')).getByText('Edit'));
    expect(screen.getByText('Edit project')).toBeInTheDocument();
    const name = screen.getByPlaceholderText('Project name');
    expect(name).toHaveValue('Beta');
    expect(screen.getByPlaceholderText('main')).toHaveValue('develop');

    fireEvent.change(name, { target: { value: 'Beta 2' } });
    await clickAsync(screen.getByText('Save'));

    expect(api.updateProject).toHaveBeenCalledWith(
      'p1',
      expect.objectContaining({ name: 'Beta 2', customerId: 'cust', owner: 'team-b' }),
    );
    expect(await screen.findByText('Beta 2')).toBeInTheDocument();
  });

  it('falls back to empty values for missing optional fields when editing', async () => {
    await renderSettings([
      project({
        customerId: undefined,
        gitUrl: undefined,
        description: undefined,
        defaultBranch: undefined,
        environment: undefined,
        owner: undefined,
      }),
    ]);
    await clickAsync(within(row('Beta')).getByText('Edit'));
    expect(screen.getByPlaceholderText('e.g. noordzee-logistics')).toHaveValue('');
    expect(screen.getByPlaceholderText('main')).toHaveValue('main');
  });

  it('cancels the form and clears a validation error', async () => {
    await renderSettings();
    await clickAsync(screen.getByText('+ New project'));
    await clickAsync(screen.getByText('Save'));
    expect(await screen.findByRole('alert')).toBeInTheDocument();

    await clickAsync(screen.getByText('Cancel'));
    expect(screen.queryByText('New project')).not.toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(api.createProject).not.toHaveBeenCalled();
  });

  it('closes the credentials form when a project form is opened', async () => {
    await renderSettings();
    vi.mocked(api.getGitCredentials).mockResolvedValue({
      credentialType: 'NONE',
      hasSecret: false,
    });
    await clickAsync(within(row('Beta')).getByText('Add credentials'));
    expect(await screen.findByText('Git credentials')).toBeInTheDocument();

    await clickAsync(screen.getByText('+ New project'));
    expect(screen.queryByText('Git credentials')).not.toBeInTheDocument();
  });
});

describe('ProjectSettings — status and git check', () => {
  beforeEach(() => vi.clearAllMocks());

  it('deactivates an active project', async () => {
    await renderSettings();
    vi.mocked(api.deactivateProject).mockResolvedValue(undefined);
    await clickAsync(within(row('Beta')).getByText('Deactivate'));
    await clickAsync(screen.getByTestId('confirm-action-confirm'));
    expect(api.deactivateProject).toHaveBeenCalledWith('p1');
    expect(await within(row('Beta')).findByText('Inactive')).toBeInTheDocument();
  });

  it('activates an inactive project', async () => {
    await renderSettings([project({ active: 0 })]);
    vi.mocked(api.activateProject).mockResolvedValue(undefined);
    await clickAsync(within(row('Beta')).getByText('Activate'));
    expect(api.activateProject).toHaveBeenCalledWith('p1');
    expect(await within(row('Beta')).findByText('Active')).toBeInTheDocument();
  });

  it('shows an error when the status change fails and leaves the project unchanged', async () => {
    await renderSettings();
    vi.mocked(api.deactivateProject).mockRejectedValue(new Error('x'));
    await clickAsync(within(row('Beta')).getByText('Deactivate'));
    await clickAsync(screen.getByTestId('confirm-action-confirm'));
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Failed to change the project status.',
    );
    expect(within(row('Beta')).getByText('Active')).toBeInTheDocument();
  });

  it('applies the git check result to the row', async () => {
    await renderSettings();
    vi.mocked(api.triggerGitCheck).mockResolvedValue({
      projectId: 'p1',
      gitStatus: 'ACCESSIBLE',
      gitStatusCheckedAt: '2026-02-02T00:00:00Z',
      gitStatusMessage: 'all good',
    });
    await clickAsync(within(row('Beta')).getByText('Git check'));
    expect(api.triggerGitCheck).toHaveBeenCalledWith('p1');
    expect(await within(row('Beta')).findByText('all good')).toBeInTheDocument();
    expect(within(row('Beta')).getByLabelText('Reachable')).toBeInTheDocument();
  });

  it('disables the git check button while it runs', async () => {
    await renderSettings();
    let resolve!: (v: api.ProjectGitStatusDto) => void;
    vi.mocked(api.triggerGitCheck).mockReturnValue(new Promise((r) => (resolve = r)));
    await clickAsync(within(row('Beta')).getByText('Git check'));
    expect(within(row('Beta')).getByRole('button', { name: 'Git check' })).toBeDisabled();
    resolve({ gitStatus: 'ACCESSIBLE' } as api.ProjectGitStatusDto);
    expect(await within(row('Beta')).findByRole('button', { name: 'Git check' })).toBeEnabled();
  });

  it('shows an error when the git check fails', async () => {
    await renderSettings();
    vi.mocked(api.triggerGitCheck).mockRejectedValue(new Error('x'));
    await clickAsync(within(row('Beta')).getByText('Git check'));
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Failed to check the Git repository.',
    );
    expect(within(row('Beta')).getByText('Git check')).toBeEnabled();
  });
});

describe('ProjectSettings — credentials', () => {
  beforeEach(() => vi.clearAllMocks());

  it('deletes credentials and updates the row', async () => {
    await renderSettings([project({ hasCredentials: true })]);
    vi.mocked(api.deleteGitCredentials).mockResolvedValue(undefined);
    await clickAsync(within(row('Beta')).getByText('Delete credentials'));
    await clickAsync(screen.getByTestId('confirm-action-confirm'));
    expect(api.deleteGitCredentials).toHaveBeenCalledWith('p1');
    expect(await within(row('Beta')).findByText('None')).toBeInTheDocument();
    expect(within(row('Beta')).getByText('Add credentials')).toBeInTheDocument();
  });

  it('shows an error when deleting credentials fails', async () => {
    await renderSettings([project({ hasCredentials: true })]);
    vi.mocked(api.deleteGitCredentials).mockRejectedValue(new Error('x'));
    await clickAsync(within(row('Beta')).getByText('Delete credentials'));
    await clickAsync(screen.getByTestId('confirm-action-confirm'));
    expect(await screen.findByRole('alert')).toHaveTextContent('Failed to delete credentials.');
    expect(within(row('Beta')).getByText('Credentials set')).toBeInTheDocument();
  });

  it('sends a typed secret, and null when the secret is left empty', async () => {
    await renderSettings();
    vi.mocked(api.getGitCredentials).mockResolvedValue({
      credentialType: 'GITHUB_PAT',
      hasSecret: true,
    });
    vi.mocked(api.saveGitCredentials).mockResolvedValue(undefined);
    await clickAsync(within(row('Beta')).getByText('Add credentials'));
    await screen.findByPlaceholderText('Leave empty to keep the current secret');

    await clickAsync(screen.getByText('Save'));
    expect(api.saveGitCredentials).toHaveBeenLastCalledWith(
      'p1',
      expect.objectContaining({ credentialType: 'GITHUB_PAT', secret: null }),
    );

    await clickAsync(within(row('Beta')).getByText('Update credentials'));
    const secret = await screen.findByPlaceholderText('Leave empty to keep the current secret');
    fireEvent.change(secret, { target: { value: 's3cret' } });
    fireEvent.change(screen.getByPlaceholderText('optional'), { target: { value: 'bot' } });
    await clickAsync(screen.getByText('Save'));
    expect(api.saveGitCredentials).toHaveBeenLastCalledWith(
      'p1',
      expect.objectContaining({ secret: 's3cret', username: 'bot' }),
    );
  });

  it('changes the credential type and closes the form on cancel', async () => {
    await renderSettings();
    vi.mocked(api.getGitCredentials).mockResolvedValue({
      credentialType: 'NONE',
      hasSecret: false,
    });
    await clickAsync(within(row('Beta')).getByText('Add credentials'));
    await screen.findByText('Type');
    chooseOption(screen.getByRole('combobox', { name: /Type/ }), 'HTTPS Token');
    expect(screen.getByRole('combobox', { name: /Type/ })).toHaveTextContent('HTTPS Token');

    await clickAsync(screen.getByText('Cancel'));
    expect(screen.queryByText('Git credentials')).not.toBeInTheDocument();
  });

  it('uses a generic message when saving fails with a non-Error', async () => {
    await renderSettings();
    vi.mocked(api.getGitCredentials).mockResolvedValue({
      credentialType: 'NONE',
      hasSecret: false,
    });
    vi.mocked(api.saveGitCredentials).mockRejectedValue('nope');
    await clickAsync(within(row('Beta')).getByText('Add credentials'));
    await screen.findByText('Type');
    await clickAsync(screen.getByText('Save'));
    expect(await screen.findByRole('alert')).toHaveTextContent('Failed to save credentials.');
  });
});

describe('ProjectSettings — with several projects', () => {
  beforeEach(() => vi.clearAllMocks());

  const two = () => [project(), project({ id: 'p2', name: 'Gamma', hasCredentials: true })];

  it('only changes the edited project', async () => {
    await renderSettings(two());
    vi.mocked(api.updateProject).mockImplementation(async (id, req) =>
      project({ id, name: req.name }),
    );
    await clickAsync(within(row('Beta')).getByText('Edit'));
    fireEvent.change(screen.getByPlaceholderText('Project name'), { target: { value: 'Beta 2' } });
    await clickAsync(screen.getByText('Save'));
    expect(await screen.findByText('Beta 2')).toBeInTheDocument();
    expect(screen.getByText('Gamma')).toBeInTheDocument();
  });

  it('only toggles the targeted project', async () => {
    await renderSettings([project(), project({ id: 'p2', name: 'Gamma', active: 0 })]);
    vi.mocked(api.deactivateProject).mockResolvedValue(undefined);
    vi.mocked(api.activateProject).mockResolvedValue(undefined);

    await clickAsync(within(row('Beta')).getByText('Deactivate'));

    await clickAsync(screen.getByTestId('confirm-action-confirm'));
    expect(await within(row('Beta')).findByText('Inactive')).toBeInTheDocument();
    expect(within(row('Gamma')).getByText('Inactive')).toBeInTheDocument();

    await clickAsync(within(row('Gamma')).getByText('Activate'));
    expect(await within(row('Gamma')).findByText('Active')).toBeInTheDocument();
    expect(within(row('Beta')).getByText('Inactive')).toBeInTheDocument();
  });

  it('only applies the git check result to the checked project', async () => {
    await renderSettings(two());
    vi.mocked(api.triggerGitCheck).mockResolvedValue({
      projectId: 'p1',
      gitStatus: 'ACCESSIBLE',
      gitStatusMessage: 'all good',
    });
    await clickAsync(within(row('Beta')).getByText('Git check'));
    expect(await within(row('Beta')).findByText('all good')).toBeInTheDocument();
    expect(within(row('Gamma')).queryByText('all good')).not.toBeInTheDocument();
  });

  it('only marks the saved project as having credentials', async () => {
    await renderSettings(two());
    vi.mocked(api.getGitCredentials).mockResolvedValue({
      credentialType: 'NONE',
      hasSecret: false,
    });
    vi.mocked(api.saveGitCredentials).mockResolvedValue(undefined);
    await clickAsync(within(row('Beta')).getByText('Add credentials'));
    await screen.findByText('Type');
    await clickAsync(screen.getByText('Save'));
    expect(await within(row('Beta')).findByText('Credentials set')).toBeInTheDocument();
    expect(within(row('Gamma')).getByText('Credentials set')).toBeInTheDocument();
  });

  it('only clears credentials of the targeted project', async () => {
    await renderSettings(two());
    vi.mocked(api.deleteGitCredentials).mockResolvedValue(undefined);
    await clickAsync(within(row('Gamma')).getByText('Delete credentials'));
    await clickAsync(screen.getByTestId('confirm-action-confirm'));
    await waitFor(() =>
      expect(within(row('Gamma')).queryByText('Credentials set')).not.toBeInTheDocument(),
    );
    expect(row('Beta')).toBeInTheDocument();
  });
});

describe('ProjectSettings — aborted load', () => {
  it('does not log an aborted load', async () => {
    vi.clearAllMocks();
    const spy = vi.spyOn(console, 'error').mockImplementation(() => {});
    vi.mocked(api.isAbortError).mockReturnValue(true);
    vi.mocked(api.loadAllProjects).mockRejectedValue(new DOMException('aborted', 'AbortError'));
    await renderAsync(<ProjectSettings onBack={vi.fn()} />);
    await waitFor(() => expect(api.loadAllProjects).toHaveBeenCalled());
    expect(spy).not.toHaveBeenCalled();
    expect(screen.getByText('Loading projects…')).toBeInTheDocument();
  });
});
