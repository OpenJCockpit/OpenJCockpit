import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { withI18n } from '@openjcockpit/i18n/testing';
import { FakeHubProjectProvider } from '@openjcockpit/hub-contract/testing';
import { appResources } from '../../i18nResources';
import SpecWorkspaceWidget from './SpecWorkspaceWidget';

vi.mock('./SpecWorkspace', () => ({
  SpecWorkspace: ({ project }: { project: { name: string } }) => <p>workspace {project.name}</p>,
}));

describe('SpecWorkspaceWidget', () => {
  it('asks for a project when none is selected', () => {
    render(
      withI18n(
        <FakeHubProjectProvider selected={null}>
          <SpecWorkspaceWidget />
        </FakeHubProjectProvider>,
        appResources,
      ),
    );
    expect(screen.getByTestId('spec-workspace-no-project')).toBeInTheDocument();
  });

  it('shows the spec workspace of the selected project', () => {
    render(
      withI18n(
        <FakeHubProjectProvider
          selected={{ id: 'p1', name: 'Alpha', gitStatus: 'ACCESSIBLE' } as never}
        >
          <SpecWorkspaceWidget />
        </FakeHubProjectProvider>,
        appResources,
      ),
    );
    expect(screen.getByText('workspace Alpha')).toBeInTheDocument();
  });
});
