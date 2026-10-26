import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import type { Project } from '@openjcockpit/api';
import { mockWorkspace } from '@openjcockpit/api/testing';
import { CustomerHero, toRepositoryPageUrl } from './CustomerHero';

const project = (gitUrl?: string): Project => ({
  id: 'p1',
  name: 'Demo',
  gitUrl,
  active: 1,
  newProject: 0,
  gitStatus: 'ACCESSIBLE',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
});

describe('toRepositoryPageUrl', () => {
  it.each([
    ['https://github.com/org/repo.git', 'https://github.com/org/repo'],
    ['https://github.com/org/repo', 'https://github.com/org/repo'],
    ['git@github.com:org/repo.git', 'https://github.com/org/repo'],
    ['https://user:secret@github.com/org/repo.git', 'https://github.com/org/repo'],
  ])('maps %s to %s', (input, expected) => {
    expect(toRepositoryPageUrl(input)).toBe(expected);
  });

  it.each([undefined, '', '   ', 'not a url', 'javascript:alert(1)', 'file:///tmp/repo'])(
    'returns undefined for %s',
    (input) => {
      expect(toRepositoryPageUrl(input)).toBeUndefined();
    },
  );
});

describe('CustomerHero', () => {
  it('links to the project repository in a new tab', () => {
    render(
      <CustomerHero
        selectedProject={project('git@github.com:org/repo.git')}
        workspace={mockWorkspace}
      />,
    );
    const link = screen.getByTestId('project-repository-link');
    expect(link).toHaveAttribute('href', 'https://github.com/org/repo');
    expect(link).toHaveAttribute('target', '_blank');
    expect(link).toHaveAttribute('rel', 'noreferrer');
    expect(link).toHaveTextContent('https://github.com/org/repo');
  });

  it('omits the link when the project has no git URL', () => {
    render(<CustomerHero selectedProject={project()} workspace={mockWorkspace} />);
    expect(screen.queryByTestId('project-repository-link')).not.toBeInTheDocument();
  });
});
