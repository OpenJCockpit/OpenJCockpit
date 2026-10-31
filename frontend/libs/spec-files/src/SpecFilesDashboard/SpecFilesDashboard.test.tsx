import { act, fireEvent, render as rtlRender, screen, waitFor } from '@testing-library/react';
import type { ReactElement } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { withQueryClient } from '../testQueryClient';
import { SpecFilesDashboard, type RenderAddToQueueArgs } from './SpecFilesDashboard';
import * as api from '@openjcockpit/api';
import type { Project, SpecFile } from '@openjcockpit/api';

vi.mock('@openjcockpit/api', async (importOriginal) =>
  (await import('../testUtils')).automockApiClients(await importOriginal()),
);
const render = (ui: ReactElement) => rtlRender(withQueryClient(ui));

const renderAddToQueue = ({ fixedSpecFile, onAdded, onCancel }: RenderAddToQueueArgs) => (
  <div role="dialog" aria-label="Add to queue">
    <span>{fixedSpecFile}</span>
    <button onClick={onAdded}>stub-add</button>
    <button onClick={onCancel}>stub-cancel</button>
  </div>
);

const PROJECT: Project = {
  id: 'project-1',
  name: 'Test Project',
  active: 1,
  newProject: 0,
  gitStatus: 'ACCESSIBLE',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
};

const SPEC: SpecFile = {
  id: 'pricing-rules',
  fileName: 'pricing-rules.md',
  owner: '',
  lastChanged: '4 jul 10:30',
  status: 'Active',
  selected: true,
  content: '# Pricing rules',
  repositoryUrl: 'https://github.com/org/repo',
};

describe('SpecFilesDashboard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('shows a hint when no project is selected', async () => {
    await act(async () => {
      render(<SpecFilesDashboard project={null} onBack={vi.fn()} />);
    });

    expect(screen.getByText(/Select a project first/)).toBeInTheDocument();
  });

  it('loads and shows specs for the given project', async () => {
    vi.mocked(api.loadProjectSpecs).mockResolvedValue([SPEC]);

    await act(async () => {
      render(
        <SpecFilesDashboard
          project={PROJECT}
          onBack={vi.fn()}
          renderAddToQueue={renderAddToQueue}
        />,
      );
    });

    expect(api.loadProjectSpecs).toHaveBeenCalledWith('project-1', expect.any(AbortSignal));
    expect(await screen.findByDisplayValue('# Pricing rules')).toBeInTheDocument();
  });

  it('disables Save until the content is edited', async () => {
    vi.mocked(api.loadProjectSpecs).mockResolvedValue([SPEC]);

    await act(async () => {
      render(
        <SpecFilesDashboard
          project={PROJECT}
          onBack={vi.fn()}
          renderAddToQueue={renderAddToQueue}
        />,
      );
    });

    const saveButton = await screen.findByText('💾 Save');
    expect(saveButton).toBeDisabled();

    const textarea = screen.getByLabelText('Contents of pricing-rules.md');
    fireEvent.change(textarea, { target: { value: '# Pricing rules v2' } });

    expect(saveButton).not.toBeDisabled();
  });

  it('saves the edited spec and shows the pull request link', async () => {
    vi.mocked(api.loadProjectSpecs).mockResolvedValue([SPEC]);
    vi.mocked(api.saveSpecFile).mockResolvedValue({
      projectId: 'project-1',
      branch: 'spec-edit/test-project/20260705-abc',
      baseBranch: 'main',
      commitHash: 'def456',
      fileName: 'pricing-rules.md',
      pullRequestUrl:
        'https://github.com/org/repo/compare/main...spec-edit/test-project/20260705-abc?expand=1',
      message: 'Changes pushed to branch spec-edit/test-project/20260705-abc',
    });

    await act(async () => {
      render(
        <SpecFilesDashboard
          project={PROJECT}
          onBack={vi.fn()}
          renderAddToQueue={renderAddToQueue}
        />,
      );
    });

    const textarea = await screen.findByLabelText('Contents of pricing-rules.md');
    fireEvent.change(textarea, { target: { value: '# Pricing rules v2' } });

    await act(async () => {
      screen.getByText('💾 Save').click();
    });

    await waitFor(() =>
      expect(api.saveSpecFile).toHaveBeenCalledWith(
        'project-1',
        'pricing-rules.md',
        '# Pricing rules v2',
      ),
    );
    expect(await screen.findByText(/Changes pushed/)).toBeInTheDocument();
    expect(screen.getByTestId('spec-add-to-queue-unmerged')).toHaveTextContent(/not merged yet/);
    expect(screen.getByTestId('spec-add-to-queue')).toBeDisabled();
    expect(screen.getByRole('link', { name: /Open pull request/ })).toHaveAttribute(
      'href',
      'https://github.com/org/repo/compare/main...spec-edit/test-project/20260705-abc?expand=1',
    );
  });

  it('shows an error message when saving fails', async () => {
    vi.mocked(api.loadProjectSpecs).mockResolvedValue([SPEC]);
    vi.mocked(api.saveSpecFile).mockRejectedValue(new Error('Failed to save spec file: 502'));

    await act(async () => {
      render(
        <SpecFilesDashboard
          project={PROJECT}
          onBack={vi.fn()}
          renderAddToQueue={renderAddToQueue}
        />,
      );
    });

    const textarea = await screen.findByLabelText('Contents of pricing-rules.md');
    fireEvent.change(textarea, { target: { value: '# broken' } });

    await act(async () => {
      screen.getByText('💾 Save').click();
    });

    expect(await screen.findByText('Failed to save spec file: 502')).toBeInTheDocument();
  });

  it('calls onBack when the back button is clicked', async () => {
    vi.mocked(api.loadProjectSpecs).mockResolvedValue([]);
    const onBack = vi.fn();

    await act(async () => {
      render(<SpecFilesDashboard project={PROJECT} onBack={onBack} />);
    });

    await act(async () => {
      screen.getByTitle('Back to dashboard').click();
    });

    expect(onBack).toHaveBeenCalledTimes(1);
  });
});

describe('SpecFilesDashboard — Add to queue', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(api.loadProjectSpecs).mockResolvedValue([SPEC]);
  });

  it('queues the selected spec through the dialog and shows a notice', async () => {
    await act(async () => {
      render(
        <SpecFilesDashboard
          project={PROJECT}
          onBack={vi.fn()}
          renderAddToQueue={renderAddToQueue}
        />,
      );
    });
    fireEvent.click(await screen.findByTestId('spec-add-to-queue'));
    expect(screen.getByRole('dialog', { name: 'Add to queue' })).toHaveTextContent(
      'pricing-rules.md',
    );
    fireEvent.click(screen.getByText('stub-add'));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.getByTestId('spec-queued-notice')).toHaveTextContent(
      'pricing-rules.md was added to the queue.',
    );
  });

  it('closes the dialog on cancel without queuing', async () => {
    await act(async () => {
      render(
        <SpecFilesDashboard
          project={PROJECT}
          onBack={vi.fn()}
          renderAddToQueue={renderAddToQueue}
        />,
      );
    });
    fireEvent.click(await screen.findByTestId('spec-add-to-queue'));
    fireEvent.click(screen.getByText('stub-cancel'));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.queryByTestId('spec-queued-notice')).not.toBeInTheDocument();
  });

  it('is disabled with a visible reason while the spec has unsaved edits', async () => {
    await act(async () => {
      render(
        <SpecFilesDashboard
          project={PROJECT}
          onBack={vi.fn()}
          renderAddToQueue={renderAddToQueue}
        />,
      );
    });
    const textarea = await screen.findByLabelText('Contents of pricing-rules.md');
    fireEvent.change(textarea, { target: { value: '# changed' } });
    const button = screen.getByTestId('spec-add-to-queue');
    expect(button).toBeDisabled();
    expect(button).toHaveAccessibleDescription(/Save the spec first/);
    expect(screen.getByText(/Save the spec first/)).toBeVisible();
  });
});

describe('SpecFilesDashboard — without the queue slot', () => {
  it('hides the add-to-queue action', async () => {
    vi.clearAllMocks();
    vi.mocked(api.loadProjectSpecs).mockResolvedValue([SPEC]);
    await act(async () => {
      render(<SpecFilesDashboard project={PROJECT} onBack={vi.fn()} />);
    });
    await screen.findByLabelText('Contents of pricing-rules.md');
    expect(screen.queryByTestId('spec-add-to-queue')).not.toBeInTheDocument();
  });

  describe('spec list', () => {
    const OTHER: SpecFile = {
      ...SPEC,
      id: 'other',
      fileName: 'other.md',
      selected: false,
      content: '# Other',
    };
    const open = async (specs: SpecFile[]) => {
      vi.mocked(api.loadProjectSpecs).mockResolvedValue(specs);
      await act(async () => {
        render(
          <SpecFilesDashboard
            project={PROJECT}
            onBack={vi.fn()}
            renderAddToQueue={renderAddToQueue}
          />,
        );
      });
    };

    it('switches to another spec and discards unsaved edits of the previous one', async () => {
      await open([SPEC, OTHER]);
      fireEvent.change(await screen.findByLabelText('Contents of pricing-rules.md'), {
        target: { value: 'edited' },
      });
      fireEvent.click(screen.getByRole('button', { name: /other\.md/ }));
      expect(await screen.findByDisplayValue('# Other')).toBeInTheDocument();
      expect(screen.getByText('💾 Save')).toBeDisabled();
      fireEvent.click(screen.getByRole('button', { name: /pricing-rules\.md/ }));
      expect(await screen.findByDisplayValue('# Pricing rules')).toBeInTheDocument();
    });

    it('selects the first spec when none is flagged as selected', async () => {
      await open([{ ...OTHER }, { ...SPEC, selected: false }]);
      expect(await screen.findByDisplayValue('# Other')).toBeInTheDocument();
    });

    it('treats specs without content as empty text', async () => {
      const { content: _omit, ...noContent } = OTHER;
      await open([{ ...SPEC, content: undefined }, noContent]);
      const textarea = await screen.findByLabelText('Contents of pricing-rules.md');
      expect(textarea).toHaveValue('');
      expect(screen.getByText('💾 Save')).toBeDisabled();
      fireEvent.click(screen.getByRole('button', { name: /other\.md/ }));
      expect(await screen.findByLabelText('Contents of other.md')).toHaveValue('');
    });

    it('shows an error with a retry that reloads the specs', async () => {
      vi.mocked(api.loadProjectSpecs).mockRejectedValueOnce(new Error('boom'));
      await act(async () => {
        render(<SpecFilesDashboard project={PROJECT} onBack={vi.fn()} />);
      });
      expect(await screen.findByTestId('specs-error')).toBeInTheDocument();

      vi.mocked(api.loadProjectSpecs).mockResolvedValue([SPEC]);
      await act(async () => {
        fireEvent.click(screen.getByRole('button', { name: /retry/i }));
      });
      expect(await screen.findByDisplayValue('# Pricing rules')).toBeInTheDocument();
      expect(screen.queryByTestId('specs-error')).not.toBeInTheDocument();
    });

    it('explains an empty repository', async () => {
      await open([]);
      expect(await screen.findByText('No Specs Found')).toBeInTheDocument();
      expect(screen.getByText('No spec selected.')).toBeInTheDocument();
    });

    it('shows a fallback message when saving rejects with a non-Error', async () => {
      await open([SPEC]);
      vi.mocked(api.saveSpecFile).mockRejectedValue('nope');
      fireEvent.change(await screen.findByLabelText('Contents of pricing-rules.md'), {
        target: { value: 'edited' },
      });
      await act(async () => {
        fireEvent.click(screen.getByText('💾 Save'));
      });
      expect(await screen.findByText('Saving the spec failed')).toBeInTheDocument();
    });
  });
});
